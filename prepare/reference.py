#!/usr/bin/env python3
"""Record the title's video or CBZ as the reference copy its document was made against.

  reference.py --document path/to/document.json [--media FILE] [--replace]

Writes the body's `reference`: the v1 fingerprint and size of the media, and for a video its
duration from ffprobe. The media defaults to the one video or CBZ beside the document.
new_title.py records this when it starts a document; run this for an existing document, or after
replacing its media with the copy the cues are timed against.

A document that already names a different fingerprint is left alone unless --replace is given,
because a different file may be a different cut that the cues no longer fit.

The fingerprint is `v1:` and the lowercase hex SHA-256 of the file size as 8 bytes big-endian,
the first 4 MiB and the last 4 MiB (the whole file once, after its size, if it is at most 8 MiB).
MediaFingerprint.java implements the same rule.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

from openrouter import load, save

END = 4 * 1024 * 1024
VIDEO = {".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".ogv"}


def fingerprint(path):
    size = path.stat().st_size
    digest = hashlib.sha256(size.to_bytes(8, "big"))
    with path.open("rb") as f:
        if size <= 2 * END:
            digest.update(f.read())
        else:
            digest.update(f.read(END))
            f.seek(size - END)
            digest.update(f.read(END))
    return "v1:" + digest.hexdigest()


def duration(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_format", "-of", "json", str(path)],
        check=True, capture_output=True, text=True).stdout
    return float(json.loads(out)["format"]["duration"])


def media_beside(document, comic):
    wanted = {".cbz"} if comic else VIDEO
    found = [p for p in document.parent.iterdir() if p.is_file() and p.suffix.lower() in wanted]
    if len(found) != 1:
        kind = "CBZ" if comic else "video"
        raise SystemExit(f"Expected one {kind} beside {document}, found {len(found)}; use --media.")
    return found[0]


def with_reference(body, reference):
    """The body with `reference` placed where the canonical form puts it: before cues or pages."""
    rest = {k: v for k, v in body.items() if k != "reference"}
    out = {}
    for key, value in rest.items():
        if key in ("cues", "pages"):
            out["reference"] = reference
        out[key] = value
    out.setdefault("reference", reference)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--media", type=Path)
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()

    document = load(args.document)
    kind = "comic" if "comic" in document else "video"
    media = args.media or media_beside(args.document, kind == "comic")
    reference = {"fingerprint": fingerprint(media), "sizeBytes": media.stat().st_size}
    if kind == "video":
        reference["durationSeconds"] = round(duration(media), 3)

    old = document[kind].get("reference") or {}
    if old.get("fingerprint") not in (None, reference["fingerprint"]) and not args.replace:
        raise SystemExit(f"{args.document} names a different reference file; pass --replace.")
    if old.get("durationSeconds") and reference.get("durationSeconds"):
        drift = reference["durationSeconds"] - old["durationSeconds"]
        if abs(drift) > 1:
            print(f"Warning: {media.name} runs {drift:+.1f}s against the recorded duration.")

    document[kind] = with_reference(document[kind], reference)
    save(args.document, document)
    print(f"{args.document}: {reference}")


if __name__ == "__main__":
    main()
