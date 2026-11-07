#!/usr/bin/env python3
"""Start the study document for a video or CBZ: an empty document.json that references the media.

  new_title.py --document DOCS/comics/naruto/t02/document.json \
      --media MEDIA/comics/naruto/Naruto-T02.cbz --title "Naruto T02" \
      [--series Naruto] [--volume 2] [--source fr] [--translation en] [--notes en] [--ltr]

The document's ID is the title in lowercase with dashes (naruto-t02) unless --id is given; it must
be unique in its documents folder. A comic lists the CBZ's page images in natural filename
order and reads right to left unless --ltr is given; a video starts with no cues. The media stays
where it is: the library links the two by the reference fingerprint once both folders are
registered and refreshed. An existing document is never replaced.
"""
import argparse
import re
import zipfile
from pathlib import Path

from openrouter import save
from reference import VIDEO, duration, fingerprint

IMAGES = (".jpg", ".jpeg", ".png", ".webp")


def natural(name):
    """Sort key putting page-2 before page-10."""
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", name)]


def pages(cbz):
    with zipfile.ZipFile(cbz) as archive:
        entries = [e for e in archive.namelist() if e.lower().endswith(IMAGES)]
    if not entries:
        raise SystemExit(f"{cbz} has no page images.")
    entries.sort(key=natural)
    return [
        {"id": f"page-{i:03d}", "entry": entry, "label": str(i), "boxes": []}
        for i, entry in enumerate(entries, 1)
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--media", type=Path, required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--id", help="the document ID; defaults to the title, dashed")
    parser.add_argument("--series")
    parser.add_argument("--volume", type=int, help="a comic's tome number")
    parser.add_argument("--source", default="fr", help="BCP 47 tag of the media's language")
    parser.add_argument("--translation", default="en")
    parser.add_argument("--notes", default="en")
    parser.add_argument("--ltr", action="store_true", help="a comic read left to right")
    args = parser.parse_args()

    if args.document.exists():
        raise SystemExit(f"{args.document} already exists.")
    media = args.media
    suffix = media.suffix.lower()
    if not media.is_file() or (suffix != ".cbz" and suffix not in VIDEO):
        raise SystemExit(f"{media} is not a video or .cbz file.")
    comic = suffix == ".cbz"
    if args.volume is not None and not comic:
        raise SystemExit("--volume is for comics.")

    ident = args.id or re.sub(r"[^a-z0-9]+", "-", args.title.lower()).strip("-")
    reference = {"fingerprint": fingerprint(media), "sizeBytes": media.stat().st_size}
    document = {
        "id": ident,
        "title": args.title.strip(),
        "sourceLanguage": args.source,
        "translationLanguage": args.translation,
        "notesLanguage": args.notes,
    }
    if args.series:
        document["series"] = args.series.strip()
    if comic:
        body = {} if args.volume is None else {"volume": args.volume}
        body["readingDirection"] = "ltr" if args.ltr else "rtl"
        body["reference"] = reference
        body["pages"] = pages(media)
        document["comic"] = body
    else:
        reference["durationSeconds"] = round(duration(media), 3)
        document["video"] = {"reference": reference, "cues": []}

    args.document.parent.mkdir(parents=True, exist_ok=True)
    save(args.document, document)
    print(f"{args.document}: {ident}, referencing {media.name}")


if __name__ == "__main__":
    main()
