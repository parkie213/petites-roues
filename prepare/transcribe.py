#!/usr/bin/env python3
"""Transcribe a registered video title through OpenRouter, in resumable chunks.

  transcribe.py --document content/my-title/document.json --run-dir DIR [--chunks 1]

Extracts compact mono audio from the video beside the document (the library's Add title keeps
only the chosen audio track) and transcribes it in the document's source language in chunks of
about ten minutes that overlap their neighbours by a few seconds. Each chunk's request and raw
response are saved under DIR/chunk-NNN; a chunk that already has a response is not sent again,
so rerunning the same command resumes after a failure.

Each segment belongs to the chunk whose own stretch of the timeline contains its midpoint, which
drops the duplicates heard in the overlaps. Cues are added to the document as untranslated cues
with IDs fixed per chunk. --chunks N stops after the first N chunks, as a sample to check; rerun
without it to add the rest. Cues already written, and any notes on them, are left alone. A
document that already has cues from elsewhere is refused.
"""
import argparse
import base64
import hashlib
import math
import subprocess
from pathlib import Path

from openrouter import cost, load, post, save

MODEL = "mistralai/voxtral-small-transcribe-3-0"
VIDEOS = {".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".ogv"}
CHUNK = 600
OVERLAP = 5


def stamp(seconds):
    ms = round(seconds * 1000)
    s, ms = divmod(ms, 1000)
    m, s = divmod(s, 60)
    h, m = divmod(m, 60)
    return f"{h:02}:{m:02}:{s:02},{ms:03}"


def duration(path):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                                 str(path)], check=True, capture_output=True, text=True).stdout)


def transcribe(chunk_dir, audio, start, length, language, model):
    """Sends one chunk unless it already has a usable response. Returns its segments, offset
    to the film timeline, or None after a failure (the response is kept for inspection)."""
    response = chunk_dir / "response.json"
    if not response.exists() or not load(response).get("segments"):
        chunk_dir.mkdir(exist_ok=True)
        clip = chunk_dir / "audio.mp3"
        subprocess.run(["ffmpeg", "-nostdin", "-y", "-v", "error", "-ss", str(start), "-t", str(length),
                        "-i", str(audio), "-c:a", "libmp3lame", "-b:a", "64k", str(clip)], check=True)
        data = clip.read_bytes()
        body = {"model": model, "language": language, "response_format": "verbose_json",
                "timestamp_granularities": ["segment"]}
        save(chunk_dir / "request.json", dict(body, offsetSeconds=start, audioSha256=hashlib.sha256(data).hexdigest()))
        body["input_audio"] = {"format": "mp3", "data": base64.b64encode(data).decode()}
        status, result, elapsed = post("audio/transcriptions", body)
        save(response, result)
        print(f"{chunk_dir.name}: HTTP {status}, {elapsed}s, {len(result.get('segments') or [])} segments, "
              f"USD {cost(result)}", flush=True)
        if not result.get("segments"):
            print(f"{chunk_dir.name} failed: {result.get('error')}", flush=True)
            return None
    return [dict(s, start=s["start"] + start, end=s["end"] + start) for s in load(response)["segments"]]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--chunks", type=int, help="transcribe only the first N chunks (a sample)")
    parser.add_argument("--model", default=MODEL)
    args = parser.parse_args()

    document = load(args.document)
    if "video" not in document:
        raise SystemExit("Not a video document")
    written_path = args.run_dir / "written.json"
    written = load(written_path) if written_path.exists() else []
    if document["video"].get("cues") and not written:
        raise SystemExit("The document already has cues from another run; nothing to transcribe")
    videos = [f for f in args.document.parent.iterdir() if f.suffix.lower() in VIDEOS]
    if len(videos) != 1:
        raise SystemExit(f"Expected one video beside the document, found {len(videos)}")

    args.run_dir.mkdir(parents=True, exist_ok=True)
    audio = args.run_dir / "audio.mp3"
    if not audio.exists():
        subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-i", str(videos[0]), "-vn", "-ac", "1", "-ar", "16000",
                        "-c:a", "libmp3lame", "-b:a", "64k", str(audio)], check=True)
    total = duration(audio)
    count = max(1, math.ceil(total / CHUNK))
    wanted = range(min(count, args.chunks or count))

    segments, done = [], []
    for k in wanted:
        if k in written:
            continue
        start = max(0, k * CHUNK - OVERLAP)
        end = min(total, (k + 1) * CHUNK + OVERLAP)
        found = transcribe(args.run_dir / f"chunk-{k + 1:03}", audio, start, end - start,
                           document["sourceLanguage"], args.model)
        if found is None:
            break
        until = (k + 1) * CHUNK if k < count - 1 else math.inf
        owned = [s for s in found if k * CHUNK <= (s["start"] + s["end"]) / 2 < until
                 and s["text"].strip() and s["end"] > s["start"]]
        segments += [dict(s, id=f"cue-{k + 1:03}-{i:03}") for i, s in enumerate(owned, 1)]
        done.append(k)

    if done:
        document["video"].setdefault("cues", []).extend(
            {"start": round(s["start"], 3), "end": round(s["end"], 3),
             "line": {"id": s["id"], "source": s["text"].strip()}} for s in segments)
        document["video"]["cues"].sort(key=lambda c: c["start"])
        document["video"]["transcriptionMethod"] = f"ASR: {args.model} via OpenRouter"
        temporary = args.document.with_name(args.document.name + ".tmp")
        save(temporary, document)
        temporary.replace(args.document)
        save(written_path, sorted(written + done))
        (args.run_dir / "transcript.srt").write_text("\n\n".join(
            f"{i}\n{stamp(c['start'])} --> {stamp(c['end'])}\n{c['line']['source']}"
            for i, c in enumerate(document["video"]["cues"], 1)) + "\n", encoding="utf-8")
    missing = [k + 1 for k in range(count) if k not in written + done]
    print(f"Added {len(segments)} cues from {len(done)} chunks to {args.document}; "
          + (f"chunks still to do: {missing[0]}–{missing[-1]} of {count}" if missing else f"all {count} chunks done"))
    if any(k not in written + done for k in wanted):
        raise SystemExit("A chunk failed; rerun the same command to resume")


if __name__ == "__main__":
    main()
