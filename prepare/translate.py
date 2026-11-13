#!/usr/bin/env python3
"""First-pass translations through OpenRouter: the first annotation stage.

  translate.py --document DOC --run-dir DIR [--all | --cues FILE] [--no-write] [--batch 30]

Works on registered video and comic documents and needs the app running: it checks answers with
the app's validator and queues the results for review. By default it translates cues that have no
translation; --all redoes every cue and --cues takes a file of cue IDs, one per line. Cues go in batches (whole comic pages where possible) with
up to eight neighbouring cues as context and agents/guides/translation-notes.md as the standard.
Existing annotations are not shown to the model; open listening and text checks are.

Later stages build on the translation: phrases.py divides each line into linked chunks and
notes.py explains them. A translated cue loses any phrase breakdown it had, because a breakdown
is bound to the exact translation.

Each cue in an answer is accepted on its own once it passes the app's validator. Up to two repair
requests then resend only the cues that failed. Every attempt is saved; rerunning the same
command resumes. Finally updates DOC in place for every accepted cue whose source text still
matches what was sent (IDs, source text, placement, timing, checks and notes are untouched),
keeps a copy as DIR/document.json, imports the file into the app and queues each cue for
review. Refuses to run while the title has an open review batch. --no-write keeps the answers
in the run directory only.
"""
import argparse
import json
from pathlib import Path

import app
import batches
from openrouter import load

MODEL = "openai/gpt-6-luna"
GUIDE = Path(__file__).resolve().parent.parent / "agents/guides/translation-notes.md"
CONTRACT = """Translate every assigned cue, following the Translation section of the guide below. This is only the first stage: phrase breakdowns and notes are written later, so return no notes. Read neighbouring cues for context. Return only assigned IDs exactly once in input order. A cue with a listeningCheck or textCheck has doubtful source text: translate what is clear and do not silently correct it. Return JSON only:
{"cues":[{"id":"assigned-id","translation":"Natural translation"}]}
No markdown fences. Final-check all IDs before returning.

TEACHING GUIDE:
"""


def slim(cue):
    line = cue["line"]
    return {k: v for k, v in {"id": line["id"], "pageId": cue.get("pageId"), "source": line["source"],
                              "continuesPrevious": line.get("continuesPrevious", False),
                              "listeningCheck": cue.get("listeningCheck"),
                              "textCheck": line.get("textCheck")}.items() if v is not None}


def annotate(line, answer):
    """Writes the translation, dropping any alignment bound to the old one."""
    line.pop("alignment", None)
    line["translation"] = answer["translation"]


def shape_errors(c):
    """Problems that stop an answer being written into a document at all; the app's validator
    reports everything else."""
    if not isinstance(c.get("translation"), str) or not c["translation"].strip():
        return ["Nonempty translation required"]
    return []


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--all", action="store_true", help="translate every cue, not just untranslated ones")
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
            raise SystemExit("The document is invalid; fix it first: "
                             + "; ".join(app.problems(title, document)[:5]))
        listed = set(args.cues.read_text(encoding="utf-8").split()) if args.cues else None
        wanted = [i for i, c in enumerate(batches.cues_of(document)) if c["line"]["source"].strip() and (
            c["line"]["id"] in listed if listed is not None else args.all or not c["line"].get("translation"))]
        batches.create(args.run_dir, document, raw, GUIDE.read_text(encoding="utf-8"), args.model, title,
                       wanted, args.batch, slim)
    manifest = load(args.run_dir / "manifest.json")
    print(f"{len(manifest['batches'])} batches with {manifest['model']}", flush=True)

    guide = (args.run_dir / "teaching-guide.md").read_text(encoding="utf-8")
    base = load(args.run_dir / "base-document.json")

    def prompt(batch):
        return CONTRACT + guide + "\n\nINPUT:\n" + json.dumps(batch, ensure_ascii=False, indent=2)

    def accept(answer, assigned, attempt):
        return batches.check(answer, assigned, title, base, shape_errors, annotate)

    spent, sent, answers, failed = batches.run_all(args.run_dir, manifest, prompt, accept, args.workers)
    print(f"Accepted {len(answers)} of {len(sent)} cues; this run cost USD {spent}")
    if args.no_write:
        print(f"Not written: answers are in {args.run_dir}/batch-*/accepted.json")
        return
    applied, changed = batches.write(args.document, args.run_dir, title, answers,
                                     lambda line: line["source"] == sent[line["id"]]["source"], annotate)
    print(f"Applied {len(applied)} cues; updated {args.document}")
    if changed:
        print(f"Skipped {len(changed)} cues whose source changed since generation: {', '.join(changed[:10])}")

    model = manifest["model"]
    requests = [{"cueId": i, "text": f"First-pass translation from {model}: review it."}
                for i in applied]
    requests += [{"cueId": i, "text": f"{model} could not translate this cue: " + "; ".join(e)[:1800]}
                 for i, e in failed.items() if i not in changed]
    queued = batches.queue(args.run_dir, title, requests)
    if queued is not None:
        print(f"Queued {queued} cues for review ({len(failed)} untranslated); "
              "next, run phrases.py for their breakdowns")


if __name__ == "__main__":
    main()
