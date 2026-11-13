#!/usr/bin/env python3
"""Record the sample comic's exact bubbles and phrases using an installed Piper voice and ffmpeg.

Run manually when wording changes; normal Pages builds only copy the committed recordings.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
DOCUMENT = ROOT / "example/documents-fr/comics/lulu/le-parapluie-de-lulu/document.json"
AUDIO = ROOT / "site/demo-audio"


def spoken_texts(document):
    """Every distinct whole bubble and pinnable source phrase, preserving its exact spelling."""
    return sorted({text for page in document["comic"]["pages"]
                   for box in page.get("boxes", []) for line in box["lines"]
                   for text in [line["source"], *line.get("alignment", {}).get("source", [])]
                   if text.strip()})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--piper", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    args = parser.parse_args()
    document = json.loads(DOCUMENT.read_text())
    AUDIO.mkdir(exist_ok=True)
    clips = {}
    with tempfile.TemporaryDirectory() as temporary:
        for text in spoken_texts(document):
            name = hashlib.sha256(text.encode()).hexdigest()[:20] + ".mp3"
            wav = Path(temporary) / "clip.wav"
            subprocess.run([str(args.piper), "--model", str(args.model), "--output_file", str(wav)],
                           input=text + "\n", text=True, check=True, capture_output=True)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav),
                            "-codec:a", "libmp3lame", "-q:a", "4", "-map_metadata", "-1", str(AUDIO / name)], check=True)
            clips[text] = name
    manifest = {"voice": args.model.name, "sourceLanguage": document["sourceLanguage"], "clips": clips}
    (AUDIO / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    for file in AUDIO.glob("*.mp3"):
        if file.name not in clips.values():
            file.unlink()
    print(f"Recorded {len(clips)} clips in {AUDIO}")


if __name__ == "__main__":
    main()
