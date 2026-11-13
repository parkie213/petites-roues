#!/usr/bin/env python3
"""First-pass learner notes for reviewed phrase breakdowns through OpenRouter.

  notes.py --document DOC --run-dir DIR [--all | --cues FILE] [--no-write] [--batch 30]

The last annotation stage, after translate.py, phrases.py and the breakdown review. Each cue's
translation, chunks and links are fixed; the model writes a note for every link and the
sentence notes, following agents/guides/translation-notes.md. By default it handles cues with a
current breakdown in which some link has no note; --all redoes every cue with a breakdown and
--cues takes a file of cue IDs, one per line. Existing notes are not shown to the model; open
listening and text checks are.

Needs the app running: each cue in an answer is accepted once it passes the app's validator, and
up to two repair requests resend only the cues that failed. Rerunning the same command resumes.
Then updates DOC in place for every accepted cue whose text and breakdown still match what was
sent, keeps a copy as DIR/document.json, imports the file into the app and queues each cue for a
notes review. Refuses to run while the title has an open review batch. --no-write keeps
the answers in the run directory only.
"""
import argparse
import json
from pathlib import Path

import app
import batches
from openrouter import load

MODEL = "openai/gpt-6-luna"
GUIDE = Path(__file__).resolve().parent.parent / "agents/guides/translation-notes.md"
CONTRACT = """Write the learner notes for every assigned cue, applying the guide below (Link notes, Sentence notes, Style, Accuracy and the traps). Each cue's translation and phrase breakdown are fixed and already reviewed: write one note for each of its links, in the order given, explaining why the source has that translated meaning here, in the declared notesLanguage, then the sentence notes (often none). Read neighbouring cues for context. Return only assigned IDs exactly once in input order. A cue with a listeningCheck or textCheck has doubtful source text: do not explain the doubtful words as if they were certain. Return JSON only:
{"cues":[{"id":"assigned-id","linkNotes":["note for link 0","note for link 1"],"notes":["one teaching point per string"]}]}
Listening and text checks are review metadata, not teaching material. Never put instructions to verify audio, references to transcripts/captions/models, or warnings about uncertainty in linkNotes or notes. A check may concern only one word, intonation, or whether text was spoken: do not discard the clear parts of the cue. Explain only the undisputed words and constructions. For a wholly disputed fragment, a brief description of its unfinished form is enough; do not invent grammar to fill it out. Leave the original check untouched.
linkNotes must have exactly one nonempty string per link. No markdown fences. Final-check all IDs, note counts and absence of review warnings before returning.

TEACHING GUIDE:
"""


def needs_notes(line):
    a = line.get("alignment")
    return bool(a) and any(not (l.get("note") or "").strip() for l in a["links"])


def slim(cue):
    """What the model sees: the cue's text and its links as chunk text; context cues without a
    breakdown show only their text."""
    line = cue["line"]
    a = line.get("alignment")
    links = [{"link": n, "source": " ".join(a["source"][i] for i in l["source"]) or None,
              "translation": " ".join(a["translation"][i] for i in l["translation"]) or None}
             for n, l in enumerate(a["links"])] if a else None
    return {k: v for k, v in {"id": line["id"], "pageId": cue.get("pageId"), "source": line["source"],
                              "translation": line.get("translation"), "links": links,
                              "continuesPrevious": line.get("continuesPrevious", False),
                              "listeningCheck": cue.get("listeningCheck"),
                              "textCheck": line.get("textCheck")}.items() if v is not None}


def annotate(line, answer):
    """Writes each link's note and the sentence notes; chunks and links are left as they are."""
    for link, note in zip(line["alignment"]["links"], answer["linkNotes"]):
        link["note"] = note
    line["notes"] = answer["notes"]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--all", action="store_true", help="redo every cue with a phrase breakdown")
    selection.add_argument("--cues", type=Path, help="file of cue IDs to do, one per line")
    parser.add_argument("--no-write", action="store_true", help="keep answers in the run directory only")
    parser.add_argument("--batch", type=int, default=30, help="cues per request")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--model", default=MODEL)
    args = parser.parse_args()
    title = app.title_id(args.document)
    if not args.no_write:
        app.require_no_open_batch(title)

    if not (args.run_dir / "manifest.json").exists():
        raw = args.document.read_bytes()
        document = json.loads(raw)
        if app.problems(title, document):
            raise SystemExit("The document is invalid; fix it first: " + "; ".join(app.problems(title, document)[:5]))
        cues = batches.cues_of(document)
        listed = set(args.cues.read_text(encoding="utf-8").split()) if args.cues else None
        ready = [i for i, c in enumerate(cues) if c["line"].get("alignment")]
        wanted = [i for i in ready if (cues[i]["line"]["id"] in listed if listed is not None else
                                       args.all or needs_notes(cues[i]["line"]))]
        if listed is not None and len(wanted) < len(listed):
            print(f"Skipping {len(listed) - len(wanted)} listed cues without a current breakdown")
        batches.create(args.run_dir, document, raw, GUIDE.read_text(encoding="utf-8"), args.model, title,
                       wanted, args.batch, slim)
    manifest = load(args.run_dir / "manifest.json")
    print(f"{len(manifest['batches'])} batches with {manifest['model']}", flush=True)

    guide = (args.run_dir / "teaching-guide.md").read_text(encoding="utf-8")
    base = load(args.run_dir / "base-document.json")
    link_counts = {c["line"]["id"]: len(c["line"]["alignment"]["links"])
                   for c in batches.cues_of(base) if c["line"].get("alignment")}

    def prompt(batch):
        return CONTRACT + guide + "\n\nINPUT:\n" + json.dumps(batch, ensure_ascii=False, indent=2)

    def shape_errors(c):
        notes, sentence = c.get("linkNotes"), c.get("notes")
        expected = link_counts.get(c.get("id"))
        if not isinstance(notes, list) or len(notes) != expected:
            return [f"linkNotes needs exactly {expected} notes, one per link in order"]
        if not all(isinstance(n, str) and n.strip() for n in notes):
            return ["Every link note must be nonempty text"]
        if not isinstance(sentence, list) or not all(isinstance(n, str) for n in sentence):
            return ["Sentence notes must be a list of strings"]
        return []

    def accept(answer, assigned, attempt):
        return batches.check(answer, assigned, title, base, shape_errors, annotate)

    spent, sent, answers, failed = batches.run_all(args.run_dir, manifest, prompt, accept, args.workers)
    print(f"Accepted {len(answers)} of {len(sent)} cues; this run cost USD {spent}")
    if args.no_write:
        print(f"Not written: answers are in {args.run_dir}/batch-*/accepted.json")
        return

    def unchanged(line):
        return {k: v for k, v in slim({"line": line}).items() if k in ("source", "translation", "links")} \
            == {k: v for k, v in sent[line["id"]].items() if k in ("source", "translation", "links")}

    applied, changed = batches.write(args.document, args.run_dir, title, answers, unchanged, annotate)
    print(f"Applied {len(applied)} cues; updated {args.document}")
    if changed:
        print(f"Skipped {len(changed)} cues whose text or breakdown changed since generation: "
              + ", ".join(changed[:10]))

    model = manifest["model"]
    requests = [{"cueId": i, "text": f"First-pass notes from {model}: review them."} for i in applied]
    requests += [{"cueId": i, "text": f"{model} could not write notes for this cue: " + "; ".join(e)[:1800]}
                 for i, e in failed.items() if i not in changed]
    queued = batches.queue(args.run_dir, title, requests)
    if queued is not None:
        print(f"Queued {queued} cues for review ({len(failed)} without notes)")


if __name__ == "__main__":
    main()
