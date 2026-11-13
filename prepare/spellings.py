#!/usr/bin/env python3
"""Correct misheard names in a transcribed document from a title's spelling list.

  spellings.py --document content/my-title/document.json --spellings content-archive/my-title/spellings.json

Transcription models spell names as they hear them ("Bigwell", "Big Web"), and the ones on
OpenRouter take no list of expected names. The spelling list maps each correct spelling to the
variants seen in the transcript:

  {"Bigweld": ["Bigwell", "Big Web"], "Rodney": ["René"]}

Write the list before reading the transcript: every character, place and invented name in the
title, from its cast list or credits, each with no variants yet ({"Bigweld": []}). --suggest then
prints the words and word pairs in the transcript that look like a listed name without being it,
with the cues they are in, so the variants can be added; it changes nothing.

Variants are replaced as whole words, case-sensitively, longest first, in cue source text. Each
change is printed. Cues that already have a translation, notes or phrase links are skipped and
listed, because changing their source would leave the annotation describing other words. Running
it again changes nothing new, so add variants as later chunks reveal them and rerun. A changed
document is imported into the running app, which needs the title registered and no review batch
open on it.
"""
import argparse
import re
from collections import defaultdict
from difflib import SequenceMatcher
from pathlib import Path

import app
from batches import cues_of
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


def suggestions(cues, spellings, threshold=0.75):
    """Runs of words that resemble a listed name but are neither it nor a known variant, as
    {(name, words): [line IDs]}. A run has as many words as the name, or one more ("Big Web")."""
    known = {v.casefold() for heard in spellings.values() for v in heard}
    known |= {name.casefold() for name in spellings}
    found = defaultdict(list)
    for cue in cues:
        line = cue["line"]
        # Elided articles and prepositions come off: "d'Harold" is Harold.
        words = [re.sub(r"^\w{1,3}['’](?=\w)", "", w)
                 for w in re.findall(r"\w+(?:['’-]\w+)*", line.get("source", ""))]
        for name in spellings:
            target = name.casefold()
            size = len(name.split())
            # Short names match too many ordinary words, so they must be closer.
            needed = threshold if len(target) > 4 else 0.85
            matched = set()  # word positions already in a match, so a longer run adds nothing
            for n in (size, size + 1):
                for i in range(len(words) - n + 1):
                    run = " ".join(words[i:i + n])
                    folded = run.casefold()
                    if (matched & set(range(i, i + n)) or abs(len(folded) - len(target)) > 3
                            or any(w.casefold() in known for w in words[i:i + n]) or folded in known):
                        continue
                    if SequenceMatcher(None, folded.replace(" ", ""), target).ratio() >= needed:
                        found[(name, run)].append(line["id"])
                        matched |= set(range(i, i + n))
    return found


def annotated(line):
    return bool(line.get("translation") or line.get("notes") or line.get("alignment"))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--spellings", type=Path, required=True)
    parser.add_argument("--dry-run", action="store_true", help="print the changes without writing")
    parser.add_argument("--suggest", action="store_true",
                        help="list transcript words resembling a listed name, and change nothing")
    args = parser.parse_args()
    document = load(args.document)
    if not (document.get("video") or document.get("comic")):
        raise SystemExit("Not a video or comic document")
    if args.suggest:
        found = suggestions(cues_of(document), load(args.spellings))
        for (name, run), lines in sorted(found.items()):
            print(f"{name} ← {run!r} ×{len(lines)}: {', '.join(lines[:5])}{' …' if len(lines) > 5 else ''}")
        print(f"{len(found)} possible variants; add the misheard ones to {args.spellings} and run without --suggest")
        return
    names, variants = pattern(load(args.spellings))
    changed, skipped = 0, []
    for cue in cues_of(document):
        line = cue["line"]
        corrected = names.sub(lambda m: variants[m.group()], line.get("source", ""))
        if corrected == line.get("source", ""):
            continue
        if annotated(line):
            skipped.append(line["id"])
            continue
        print(f"{line['id']}: {line['source']}\n{' ' * len(line['id'])}→ {corrected}")
        line["source"] = corrected
        changed += 1
    if skipped:
        print(f"Skipped {len(skipped)} annotated cues; correct them in the player: {', '.join(skipped)}")
    if changed and not args.dry_run:
        title = app.title_id(args.document)
        app.require_no_open_batch(title)
        temporary = args.document.with_name(args.document.name + ".tmp")
        save(temporary, document)
        temporary.replace(args.document)
        app.import_title(title)
    print(f"{'Would change' if args.dry_run else 'Changed'} {changed} cues in {args.document}")


if __name__ == "__main__":
    main()
