#!/usr/bin/env python3
"""Split one aligned comic cue holding a paragraph into a text block of sentence cues.

  split_block.py --document path/to/document.json --cue CUE_ID --block NAME

The cue's phrase breakdown decides the sentences: a sentence ends after a source chunk ending
in `.`, `!`, `?` or `…` (closing quotes allowed). Each link must stay inside one sentence on
both sides, or nothing is written. Every sentence becomes a cue with the block's box, the
next page orders and its own translation, chunks and link notes; the first keeps the original
id, the rest get new ones. Later cues on the page move down to make room. Sentence notes of the
paragraph go to the first sentence, with a reminder to move them where they belong.

Writes the document in place; the app's validator checks it on the next load or review.
"""
import argparse
import copy
import json
import re
import uuid
from pathlib import Path

SENTENCE_END = re.compile(r"[.!?…][»”\"’)]*$")


def sentence_spans(chunks):
    """Index ranges of chunks forming each sentence, in order."""
    spans, start = [], 0
    for i, chunk in enumerate(chunks):
        if SENTENCE_END.search(chunk.strip()):
            spans.append(range(start, i + 1))
            start = i + 1
    if start < len(chunks):
        spans.append(range(start, len(chunks)))
    return spans


def split(study):
    """Sentence studies from one aligned paragraph study; raises if a link crosses sentences."""
    alignment = study.get("alignment")
    if not alignment:
        raise SystemExit("The cue needs a phrase breakdown to split by sentence.")
    source_spans = sentence_spans(alignment["source"])
    of_source = {i: s for s, span in enumerate(source_spans) for i in span}
    # A sentence's English chunks are the ones its links point at; they must be contiguous.
    english = [set() for _ in source_spans]
    for link in alignment["links"]:
        sentences = {of_source[i] for i in link["source"]}
        if len(sentences) != 1:
            raise SystemExit(f"A link spans sentences: {link['source']}")
        english[sentences.pop()].update(link["translation"])
    translation_spans = []
    previous_end = 0
    for s, chunks in enumerate(english):
        if not chunks:
            raise SystemExit(f"Sentence {s + 1} has no English chunks.")
        first, last = min(chunks), max(chunks)
        if first != previous_end or len(chunks) != last - first + 1:
            raise SystemExit(f"Sentence {s + 1}'s English is not one ordered stretch.")
        translation_spans.append(range(first, last + 1))
        previous_end = last + 1
    if previous_end != len(alignment["translation"]):
        raise SystemExit("Some English chunks belong to no sentence.")

    kept = {
        k: v
        for k, v in study.items()
        if k not in ("id", "source", "translation", "notes", "alignment")
    }
    sentences = []
    for s, (src, eng) in enumerate(zip(source_spans, translation_spans)):
        source = [alignment["source"][i] for i in src]
        translation = [alignment["translation"][i] for i in eng]
        links = []
        for link in alignment["links"]:
            if link["source"][0] in src:
                moved = copy.deepcopy(link)
                moved["source"] = [i - src.start for i in link["source"]]
                moved["translation"] = [i - eng.start for i in link["translation"]]
                links.append(moved)
        sentences.append(
            {
                "id": study["id"] if s == 0 else str(uuid.uuid4()),
                "source": " ".join(source),
                "translation": " ".join(translation),
                "notes": list(study.get("notes") or []) if s == 0 else [],
                "alignment": {
                    "sourceText": " ".join(source),
                    "translationText": " ".join(translation),
                    "source": source,
                    "translation": translation,
                    "links": links,
                },
                **copy.deepcopy(kept),
            }
        )
    return sentences


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--document", required=True, type=Path)
    parser.add_argument("--cue", required=True)
    parser.add_argument("--block", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.block):
        raise SystemExit("Block names use letters, numbers, underscores or hyphens.")

    document = json.loads(args.document.read_text(encoding="utf-8"))
    cues = document["comic"]["cues"]
    index = next((i for i, c in enumerate(cues) if c["study"]["id"] == args.cue), None)
    if index is None:
        raise SystemExit(f"No cue {args.cue}.")
    cue = cues[index]
    if cue.get("block"):
        raise SystemExit(f"The cue is already in block {cue['block']}.")
    if any(
        c.get("block") == args.block and c["pageId"] == cue["pageId"] for c in cues
    ):
        raise SystemExit(f"Page {cue['pageId']} already has a block {args.block}.")

    sentences = split(cue["study"])
    extra = len(sentences) - 1
    for other in cues:
        if other["pageId"] == cue["pageId"] and other["order"] > cue["order"]:
            other["order"] += extra
    replacement = [
        {
            **{k: v for k, v in cue.items() if k not in ("study", "order", "block")},
            "study": study,
            "order": cue["order"] + n,
            "block": args.block,
        }
        for n, study in enumerate(sentences)
    ]
    cues[index : index + 1] = replacement
    args.document.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"{cue['pageId']}: block {args.block}, {len(sentences)} sentences")
    for study in sentences:
        print(f"  {study['source']}")
    if cue["study"].get("notes"):
        print("Moved the paragraph's sentence notes to the first sentence: place them.")


if __name__ == "__main__":
    main()
