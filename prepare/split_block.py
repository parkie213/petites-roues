#!/usr/bin/env python3
"""Split one aligned comic line holding a paragraph into one line per sentence.

  split_block.py --document path/to/document.json --find [--min-length 150]
  split_block.py --document path/to/document.json --line LINE_ID

The line's phrase breakdown decides the sentences: a sentence ends after a source chunk ending
in `.`, `!`, `?`, `…` or a full-width `。！？` (closing quotes allowed). Each link must stay
inside one sentence on both sides, or nothing is written. The sentences replace the line in its box, in place, each
with its own translation, chunks and link notes; the box, its id and its reading order are
unchanged, and readers show a box with several lines a sentence at a time. The first sentence
keeps the line's id; the rest get new UUIDs. Each sentence note of the paragraph goes to the
sentence containing most of the words it quotes in backticks, or to the first sentence, with a
reminder to place it, when it quotes none.

`--find` lists candidates without writing: lines of at least `--min-length` characters whose text
holds more than one sentence. A speech balloon of two short sentences can stay whole; split
printed text blocks such as a recap or character profile.

Writes the document in place; the app's validator checks it on the next load or review.
"""
import argparse
import copy
import json
import re
import uuid
from pathlib import Path

from languages import chunk_separator

SENTENCE_END = re.compile(r"[.!?…。！？][»”\"’)」』）]*$")


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


def quoted_words(note):
    """Lower-case pieces a note quotes in backticks, with `…` gaps dropped."""
    return [piece for quote in re.findall(r"`([^`]+)`", note)
            for piece in re.split(r"\s*…\s*", quote.lower()) if piece]


def place_notes(notes, sentences):
    """Append each note to the sentence quoting most of its words; return notes quoting none."""
    unplaced = []
    for note in notes:
        words = quoted_words(note)
        def score(sentence):
            text = sentence["source"].lower()
            return sum(bool(re.search(r"(?<!\w)" + re.escape(w) + r"(?!\w)", text)) for w in words)
        best = max(sentences, key=score)
        if score(best) == 0:
            best = sentences[0]
            unplaced.append(note)
        best["notes"].append(note)
    return unplaced


def sentence_count(text):
    """Sentences in plain text, by the same end marks as chunks."""
    return len([s for s in re.split(r"(?<=[.!?…。！？])[»”\"’)」』）]*\s+", text.strip()) if s])


def find(document, min_length):
    """Lines long enough, and holding several sentences, to be split."""
    for page in document["comic"]["pages"]:
        for box in page.get("boxes", []):
            for line in box["lines"]:
                source = line.get("source") or ""
                alignment = line.get("alignment")
                count = (len(sentence_spans(alignment["source"])) if alignment
                         else sentence_count(source))
                if len(source) >= min_length and count > 1:
                    yield page, line, count, bool(alignment)


def split(line, source_join=" ", translation_join=" "):
    """Sentence lines from one aligned paragraph line; raises if a link crosses sentences."""
    alignment = line.get("alignment")
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
        for k, v in line.items()
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
                "id": line["id"] if s == 0 else str(uuid.uuid4()),
                "source": source_join.join(source),
                "translation": translation_join.join(translation),
                "notes": [],
                "alignment": {
                    "source": source,
                    "translation": translation,
                    "links": links,
                },
                **copy.deepcopy(kept),
            }
        )
    return sentences, place_notes(line.get("notes") or [], sentences)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--document", required=True, type=Path)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--line", help="split this line")
    action.add_argument("--find", action="store_true", help="list lines to split, without writing")
    parser.add_argument("--min-length", type=int, default=150,
                        help="shortest line --find lists (default: 150 characters)")
    args = parser.parse_args()

    document = json.loads(args.document.read_text(encoding="utf-8"))
    if args.find:
        for page, line, count, aligned in find(document, args.min_length):
            status = "" if aligned else " (needs a breakdown first)"
            print(f"{page['id']} {line['id']} {count} sentences{status}: {line['source'][:60]}…")
        return
    found = [(page, box, i) for page in document["comic"]["pages"] for box in page.get("boxes", [])
             for i, line in enumerate(box["lines"]) if line["id"] == args.line]
    if not found:
        raise SystemExit(f"No comic line {args.line}.")
    page, box, index = found[0]
    line = box["lines"][index]
    sentences, unplaced = split(line, chunk_separator(document["sourceLanguage"]),
                      chunk_separator(document["translationLanguage"]))
    if len(sentences) < 2:
        raise SystemExit("The line is a single sentence; nothing to split.")
    box["lines"][index : index + 1] = sentences
    args.document.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"{page['id']}: {len(sentences)} sentences in box {box['id']}")
    for sentence in sentences:
        print(f"  {sentence['source']}")
    for note in unplaced:
        print(f"Placed on the first sentence, as it quotes none of them: {note[:70]}…")


if __name__ == "__main__":
    main()
