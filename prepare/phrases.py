#!/usr/bin/env python3
"""First-pass phrase breakdowns (chunks and links, no notes) through OpenRouter.

  phrases.py --document DOC --run-dir DIR [--all | --cues FILE] [--no-write] [--batch 30]

The stage after translate.py and before notes.py. Each cue keeps its translation; the model
divides the source and the translation into chunks and links them, following the chunking and
link rules of agents/guides/translation-notes.md. It writes no notes: the breakdown is reviewed
first, and notes.py explains the reviewed chunks. By default it handles translated cues that have
no alignment or a stale one; --all redoes every translated cue and --cues takes a file of cue
IDs, one per line. Existing breakdowns are not shown to the model.

Needs the app running: each cue in an answer is accepted once it passes the app's validator.
A chunk of LONG or more words is sent back once, asking for it to be split at a clause or
adverbial boundary or kept whole only when the English wraps around it; the second answer is
accepted either way and any long chunk left is named in the cue's review request. Up to two
repair requests resend only the cues that failed. Rerunning the same command resumes.

Then updates DOC in place for every accepted cue whose source and translation still match
what was sent, and queues each one for a breakdown review. --no-write keeps the answers in the run
directory only, for comparing prompts or models (see phrase_report.py).
"""
import argparse
import json
from pathlib import Path

import app
import batches
from openrouter import load

MODEL = "openai/gpt-6-luna"
LONG = 7
GUIDE = Path(__file__).resolve().parent.parent / "agents/guides/translation-notes.md"
CONTRACT = """Write the phrase breakdown for every assigned cue, applying the Chunking and Links sections of the guide below. Each cue's translation is fixed: divide that exact text. This stage writes no notes; they are written later for the reviewed chunks. Read neighbouring cues for context. Return only assigned IDs exactly once in input order. Return JSON only:
{"cues":[{"id":"assigned-id","alignment":{"source":["exact source chunks"],"translation":["translation chunks"],"links":[{"source":[0],"translation":[0]}]}}]}
Chunks joined with single spaces must exactly reproduce source/translation respectively, punctuation included. Every chunk must belong to exactly one link, translation indices in each link must be contiguous. Indices are zero-based. No markdown fences. Final-check all IDs, joins and links before returning.

TEACHING GUIDE:
"""


def slim(cue):
    study = cue["study"]
    return {k: v for k, v in {"id": study["id"], "pageId": cue.get("pageId"), "source": study["source"],
                              "translation": study.get("translation"),
                              "continuesPrevious": study.get("continuesPrevious", False),
                              "listeningCheck": cue.get("listeningCheck"),
                              "textCheck": study.get("textCheck")}.items() if v is not None}


def annotate(study, answer):
    """Writes an answer's chunks and links into a cue, without link notes; notes.py later writes
    them and replaces the sentence notes."""
    a = answer["alignment"]
    study.update(alignmentStale=False,
                 alignment={"sourceText": study["source"], "translationText": study["translation"],
                            "source": a["source"], "translation": a["translation"],
                            "links": [{"source": l["source"], "translation": l["translation"]} for l in a["links"]]})


def shape_errors(c):
    """Problems that stop an answer being written into a document at all; the app's validator
    reports everything else."""
    def strings(x):
        return isinstance(x, list) and all(isinstance(i, str) for i in x)

    def indices(x):
        return isinstance(x, list) and all(type(i) is int and i >= 0 for i in x)
    a = c.get("alignment")
    if not isinstance(a, dict) or not strings(a.get("source")) or not strings(a.get("translation")) \
            or not isinstance(a.get("links"), list):
        return ["Alignment needs source and translation chunk lists and a links list"]
    if not all(isinstance(l, dict) and indices(l.get("source")) and indices(l.get("translation"))
               for l in a["links"]):
        return ["Each link needs source and translation index lists"]
    return []


def words(chunk):
    return sum(any(ch.isalnum() for ch in token) for token in chunk.split())


def long_chunks(answer):
    return [chunk for chunk in answer["alignment"]["source"] if words(chunk) >= LONG]


def lint(answer):
    return [f"Chunk “{chunk}” has {words(chunk)} words. Split off any clause, time or place expression, "
            "adverbial or gerund that has its own unbroken English. Keep it whole only if the English "
            "wraps around it." for chunk in long_chunks(answer)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--all", action="store_true", help="redo every translated cue")
    selection.add_argument("--cues", type=Path, help="file of cue IDs to do, one per line")
    parser.add_argument("--no-write", action="store_true", help="keep answers in the run directory only")
    parser.add_argument("--batch", type=int, default=30, help="cues per request")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--model", default=MODEL)
    args = parser.parse_args()
    title = app.title_id(args.document)

    if not (args.run_dir / "manifest.json").exists():
        raw = args.document.read_bytes()
        document = json.loads(raw)
        if app.problems(title, document):
            raise SystemExit("The document is invalid before annotation; fix it first: "
                             + "; ".join(app.problems(title, document)[:5]))
        cues = batches.cues_of(document)
        listed = set(args.cues.read_text(encoding="utf-8").split()) if args.cues else None
        if listed is not None and listed - {c["study"]["id"] for c in cues}:
            raise SystemExit("Unknown cue IDs: " + ", ".join(sorted(listed - {c["study"]["id"] for c in cues})))
        wanted = [i for i, c in enumerate(cues) if c["study"].get("translation") and (
            c["study"]["id"] in listed if listed is not None else
            args.all or not c["study"].get("alignment") or c["study"].get("alignmentStale"))]
        batches.create(args.run_dir, document, raw, GUIDE.read_text(encoding="utf-8"), args.model, title,
                       wanted, args.batch, slim)
    manifest = load(args.run_dir / "manifest.json")
    print(f"{len(manifest['batches'])} batches with {manifest['model']}", flush=True)

    guide = (args.run_dir / "teaching-guide.md").read_text(encoding="utf-8")
    base = load(args.run_dir / "base-document.json")

    def prompt(batch):
        return CONTRACT + guide + "\n\nINPUT:\n" + json.dumps(batch, ensure_ascii=False, indent=2)

    def accept(answer, assigned, attempt):
        return batches.check(answer, assigned, title, base, shape_errors, annotate,
                             lint if attempt == 1 else None)

    spent, sent, answers, failed = batches.run_all(args.run_dir, manifest, prompt, accept, args.workers)
    still_long = {i: long_chunks(a) for i, a in answers.items() if long_chunks(a)}
    print(f"Accepted {len(answers)} of {len(sent)} cues ({len(still_long)} still with a chunk of {LONG}+ words); "
          f"this run cost USD {spent}")
    if args.no_write:
        print(f"Not written: answers are in {args.run_dir}/batch-*/accepted.json")
        return

    applied, changed = batches.write(
        args.document, args.run_dir, title, answers,
        lambda s: (s["source"], s.get("translation")) == (sent[s["id"]]["source"], sent[s["id"]]["translation"]),
        annotate)
    print(f"Applied {len(applied)} cues; updated {args.document}")
    if changed:
        print(f"Skipped {len(changed)} cues whose source or translation changed since generation: "
              + ", ".join(changed[:10]))

    model = manifest["model"]
    requests = [{"cueId": i, "text": f"First-pass phrase breakdown from {model}: check chunks and links only; "
                 "notes come later."
                 + "".join(f" Long chunk kept: “{c}”." for c in still_long.get(i, []))} for i in applied]
    requests += [{"cueId": i, "text": f"{model} could not write a phrase breakdown: " + "; ".join(e)[:1800]}
                 for i, e in failed.items() if i not in changed]
    queued = batches.queue(args.run_dir, title, requests)
    if queued is not None:
        print(f"Queued {queued} cues for review ({len(failed)} without a breakdown)")


if __name__ == "__main__":
    main()
