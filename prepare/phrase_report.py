#!/usr/bin/env python3
"""Compare phrase breakdowns from phrases.py runs with a document's current ones.

  phrase_report.py --document DOC --out REPORT.md RUN_DIR [RUN_DIR ...]

For the cues sent in the runs, prints the chunk sizes of the current breakdowns and of each
run, and writes a Markdown report showing every cue's current breakdown followed by each
run's, with any link notes. Calls no model and changes nothing.
"""
import argparse
import collections
from pathlib import Path

from batches import cues_of
from openrouter import load
from phrases import LONG, words


def accepted(run_dir):
    answers = {}
    for name in load(run_dir / "manifest.json")["batches"]:
        path = run_dir / name / "accepted.json"
        if path.exists():
            answers.update((k, v["alignment"]) for k, v in load(path).items())
    return answers


def stats(alignments):
    sizes = [words(chunk) for a in alignments for chunk in a["source"]]
    if not sizes:
        return "no breakdowns"
    counts = collections.Counter(min(n, LONG) for n in sizes)
    histogram = " ".join(f"{n}{'+' if n == LONG else ''}:{counts[n]}" for n in sorted(counts))
    return (f"{len(alignments)} cues, {len(sizes)} chunks ({len(sizes) / len(alignments):.1f} per cue), "
            f"{counts[LONG]} of {LONG}+ words, {counts[1]} of one word · words per chunk {histogram}")


def table(alignment):
    if not alignment:
        return "_No breakdown._\n"
    rows = ["| French | English | Note |", "|---|---|---|"]
    for link in alignment["links"]:
        french = " ".join(alignment["source"][i] for i in link["source"]) or "—"
        english = " ".join(alignment["translation"][i] for i in link["translation"]) or "—"
        note = (link.get("note") or "").replace("|", "/").replace("\n", " ")
        rows.append(f"| {french} | {english} | {note} |")
    return "\n".join(rows) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("runs", type=Path, nargs="+")
    args = parser.parse_args()

    runs = {run: (load(run / "manifest.json")["model"], accepted(run)) for run in args.runs}
    sent = {cue["id"] for run in args.runs for name in load(run / "manifest.json")["batches"]
            for cue in load(run / name / "input.json")["assign"]}
    annotated = [c["line"] for c in cues_of(load(args.document)) if c["line"]["id"] in sent]

    lines = [f"# Phrase breakdowns: {len(annotated)} cues", ""]
    summary = [("current", stats([s["alignment"] for s in annotated if s.get("alignment")]))]
    summary += [(f"{run.name} ({model})", stats(list(answers.values()))) for run, (model, answers) in runs.items()]
    for name, text in summary:
        print(f"{name}: {text}")
        lines.append(f"- **{name}**: {text}")
    for s in annotated:
        lines += ["", f"## {s['id']}", "", f"> {s['source']}", ">", f"> {s['translation']}", ""]
        lines += ["**Current**", "", table(s.get("alignment"))]
        for run, (model, answers) in runs.items():
            lines += [f"**{model}** ({run.name})", "", table(answers.get(s["id"]))]
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
