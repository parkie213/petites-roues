#!/usr/bin/env python3
"""Correct misheard names in a transcribed document from a title's spelling list.

  spellings.py --document content/my-title/document.json --spellings content-archive/my-title/spellings.json

Transcription models spell names as they hear them ("Bigwell", "Big Web"), and the ones on
OpenRouter take no list of expected names. The spelling list maps each correct spelling to the
variants seen in the transcript:

  {"Bigweld": ["Bigwell", "Big Web"], "Rodney": ["René"]}

Variants are replaced as whole words, case-sensitively, longest first, in cue source text. Each
change is printed. Cues that already have a translation, notes or phrase links are skipped and
listed, because changing their source would leave the annotation describing other words. Running
it again changes nothing new, so add variants as later chunks reveal them and rerun.
"""
import argparse
import re
from pathlib import Path

from openrouter import load, save


def pattern(spellings):
    variants = {}
    for correct, heard in spellings.items():
        for variant in heard:
            if variant != correct:
                if variants.get(variant, correct) != correct:
                    raise SystemExit(f"{variant!r} is listed under both {variants[variant]!r} and {correct!r}")
                variants[variant] = correct
    if not variants:
        raise SystemExit("The spelling list has no variants")
    words = "|".join(re.escape(v) for v in sorted(variants, key=len, reverse=True))
    return re.compile(rf"(?<!\w)(?:{words})(?!\w)"), variants


def annotated(study):
    return bool(study.get("translation") or study.get("notes") or study.get("alignment"))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--spellings", type=Path, required=True)
    parser.add_argument("--dry-run", action="store_true", help="print the changes without writing")
    args = parser.parse_args()
    document = load(args.document)
    body = document.get("video") or document.get("comic")
    if body is None:
        raise SystemExit("Not a video or comic document")
    names, variants = pattern(load(args.spellings))
    changed, skipped = 0, []
    for cue in body.get("cues") or []:
        study = cue["study"]
        corrected = names.sub(lambda m: variants[m.group()], study.get("source", ""))
        if corrected == study.get("source", ""):
            continue
        if annotated(study):
            skipped.append(study["id"])
            continue
        print(f"{study['id']}: {study['source']}\n{' ' * len(study['id'])}→ {corrected}")
        study["source"] = corrected
        changed += 1
    if skipped:
        print(f"Skipped {len(skipped)} annotated cues; correct them in the player: {', '.join(skipped)}")
    if changed and not args.dry_run:
        temporary = args.document.with_name(args.document.name + ".tmp")
        save(temporary, document)
        temporary.replace(args.document)
    print(f"{'Would change' if args.dry_run else 'Changed'} {changed} cues in {args.document}")


if __name__ == "__main__":
    main()
