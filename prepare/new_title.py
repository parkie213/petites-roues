#!/usr/bin/env python3
"""Start the study document for a video or CBZ: an empty document.json that references the media.

  new_title.py --document DOCS/comics/naruto/t02/document.json \
      --media MEDIA/comics/naruto/Naruto-T02.cbz --title "Naruto T02" \
      [--series Naruto] [--volume 2] [--source fr] [--translation en] [--notes en] [--ltr]

The running app builds the document and this script saves it; the app never writes document
files. The media must already be in the app's media index: register its folder and Refresh first.
The document's ID is the title in lowercase with dashes (naruto-t02) unless --id is given; the app
refuses an ID that a title in its library already has. A comic lists the CBZ's page images in
natural filename order and reads right to left unless --ltr is given; a video starts with no cues,
and is refused when its first audio track, the one browsers play, is tagged with another language
than --source. The media stays where it is: the library links the two by the reference fingerprint
once the documents folder is registered and refreshed. An existing document is never replaced.
"""
import argparse
from pathlib import Path

from app import skeleton
from openrouter import save


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
    parser.add_argument("--ignore-track-language", action="store_true",
                        help="start a video whose first audio track is tagged with another language")
    args = parser.parse_args()

    if args.document.exists():
        raise SystemExit(f"{args.document} already exists.")
    document = skeleton({
        "media": str(args.media.resolve()),
        "title": args.title,
        "id": args.id,
        "series": args.series,
        "volume": args.volume,
        "sourceLanguage": args.source,
        "translationLanguage": args.translation,
        "notesLanguage": args.notes,
        "leftToRight": args.ltr,
        "ignoreTrackLanguage": args.ignore_track_language,
    })

    args.document.parent.mkdir(parents=True, exist_ok=True)
    save(args.document, document)
    print(f"{args.document}: {document['id']}, referencing {args.media.name}")


if __name__ == "__main__":
    main()
