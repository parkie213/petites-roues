#!/usr/bin/env python3
"""First-pass translations, learner notes and phrase alignments through OpenRouter.

  notes.py --document DOC --run-dir DIR [--all] [--batch 30]

Works on registered video and comic documents and needs the app running: it checks answers with
the app's validator and queues the results for review. By default it annotates cues that have no
translation or whose alignment is stale; --all redoes every cue. Cues go in batches (whole comic
pages where possible) with up to eight neighbouring cues as context and
agents/guides/translation-notes.md as the teaching standard. Existing annotations are not shown
to the model; open listening and text checks are.

Each cue in an answer is accepted on its own once it passes the app's validator. Up to two repair
requests then resend only the cues that failed. Every attempt is saved; rerunning the same
command resumes.

Finally updates DOC in place: every accepted cue whose source text still matches what was sent
gets its translation, notes and alignment. IDs, source text, placement, timing and checks are
untouched; a copy of the result is kept as DIR/document.json. Every annotated cue, and every cue
that still failed, is queued as a review request for the reviewing agent.
"""
import argparse
import copy
import hashlib
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import app
from openrouter import chat_json, cost, load, save

MODEL = "openai/gpt-6-luna"
REPAIRS = 2
GUIDE = Path(__file__).resolve().parent.parent / "agents/guides/translation-notes.md"
CONTRACT = """Apply the following teaching guide to every assigned cue. Read neighbouring cues for context. Return only assigned IDs exactly once in input order. Preserve source strings exactly; do not silently correct them. A cue with a listeningCheck or textCheck has doubtful source text: translate it, but do not explain the doubtful words as if they were certain. Return JSON only:
{"cues":[{"id":"assigned-id","translation":"Natural translation","notes":[],"alignment":{"source":["exact source chunks"],"translation":["translation chunks"],"links":[{"source":[0],"translation":[0],"note":"Compact beginner explanation"}]}}]}
Chunks joined with single spaces must exactly reproduce source/translation respectively, punctuation included. Every chunk must belong to exactly one link, each link must have a note, translation indices in each link must be contiguous. Indices are zero-based. No markdown fences. Final-check all IDs, joins and links before returning.

TEACHING GUIDE:
"""


def cues_of(document):
    return (document.get("video") or document.get("comic"))["cues"]


def slim(cue):
    study = cue["study"]
    return {k: v for k, v in {"id": study["id"], "pageId": cue.get("pageId"), "source": study["source"],
                              "continuesPrevious": study.get("continuesPrevious", False),
                              "listeningCheck": cue.get("listeningCheck"),
                              "textCheck": study.get("textCheck")}.items() if v is not None}


def plan(document, everything, size):
    """Batches of cue indices, keeping comic pages together where they fit."""
    cues = cues_of(document)
    wanted = [i for i, c in enumerate(cues) if c["study"]["source"].strip() and (
        everything or not c["study"].get("translation") or c["study"].get("alignmentStale"))]
    groups = {}
    for i in wanted:
        groups.setdefault(cues[i].get("pageId", i // size), []).append(i)
    batches, current = [], []
    for ids in groups.values():
        if current and len(current) + len(ids) > size:
            batches.append(current)
            current = []
        for i in ids:
            current.append(i)
            if len(current) >= size:
                batches.append(current)
                current = []
    return batches + [current] if current else batches


def annotate(study, answer):
    """Writes an answer's fields into a cue, copying only the document format's alignment keys."""
    a = answer["alignment"]
    study.update(translation=answer["translation"], notes=answer["notes"], alignmentStale=False,
                 alignment={"sourceText": study["source"], "translationText": answer["translation"],
                            "source": a["source"], "translation": a["translation"],
                            "links": [{"source": l["source"], "translation": l["translation"], "note": l["note"]}
                                      for l in a["links"]]})


def shape_errors(c):
    """Problems that stop an answer being written into a document at all; the app's validator
    reports everything else."""
    def strings(x):
        return isinstance(x, list) and all(isinstance(i, str) for i in x)

    def indices(x):
        return isinstance(x, list) and all(type(i) is int and i >= 0 for i in x)
    a = c.get("alignment")
    if not isinstance(c.get("translation"), str) or not c["translation"].strip():
        return ["Nonempty translation required"]
    if not strings(c.get("notes")):
        return ["Sentence notes must be a list of strings"]
    if not isinstance(a, dict) or not strings(a.get("source")) or not strings(a.get("translation")) \
            or not isinstance(a.get("links"), list):
        return ["Alignment needs source and translation chunk lists and a links list"]
    if not all(isinstance(l, dict) and indices(l.get("source")) and indices(l.get("translation"))
               and isinstance(l.get("note"), str) for l in a["links"]):
        return ["Each link needs source and translation index lists and a note"]
    return []


def check(answer, assigned, title, base):
    """Per-cue errors for one answer: {cue id: [messages]}; an empty list means accepted."""
    cues = answer.get("cues") if isinstance(answer, dict) else None
    given = {c.get("id"): c for c in cues if isinstance(c, dict)} if isinstance(cues, list) else {}
    errors = {c["id"]: (["Missing from the answer"] if c["id"] not in given else shape_errors(given[c["id"]]))
              for c in assigned}
    candidate = copy.deepcopy(base)
    for cue in cues_of(candidate):
        cue_id = cue["study"]["id"]
        if cue_id in errors and not errors[cue_id]:
            annotate(cue["study"], given[cue_id])
    for problem in app.problems(title, candidate):
        cue_id, _, message = problem.partition(": ")
        if cue_id in errors:
            errors[cue_id].append(message)
        else:
            raise SystemExit("The candidate document is invalid outside the assigned cues: " + problem)
    return errors, given


def run(batch_dir, prompt, model, title, base):
    """Generate one batch, accepting cues one by one and resending only failing cues, at most
    REPAIRS times. Returns reported cost."""
    if (batch_dir / "done.json").exists():
        return 0
    batch = load(batch_dir / "input.json")
    accepted = load(batch_dir / "accepted.json") if (batch_dir / "accepted.json").exists() else {}
    spent, errors = 0, {}
    for attempt in range(1, REPAIRS + 2):
        out = batch_dir / f"attempt-{attempt}"
        if (out / "result.json").exists():
            errors = load(out / "result.json")["errors"]
            continue
        assigned = [c for c in batch["assign"] if c["id"] not in accepted]
        if not assigned:
            break
        out.mkdir(exist_ok=True)
        text = prompt(dict(batch, assign=assigned))
        if errors:
            text += ("\n\nA previous answer for these cues was rejected. Avoid these errors:\n"
                     + json.dumps({k: v for k, v in errors.items() if k not in accepted}, ensure_ascii=False))
        settings = {"model": model, "temperature": 0, "max_tokens": 24000}
        messages = [{"role": "user", "content": text}]
        save(out / "request.json", dict(settings, messages=messages))
        status, data, elapsed, answer, error = chat_json(dict(settings, messages=messages))
        save(out / "response.json", data)
        spent += cost(data)
        if answer is None:
            errors = {c["id"]: [error] for c in assigned}
        else:
            errors, given = check(answer, assigned, title, base)
            accepted.update((k, given[k]) for k, v in errors.items() if not v)
            errors = {k: v for k, v in errors.items() if v}
            save(batch_dir / "accepted.json", accepted)
        save(out / "result.json", {"status": status, "elapsedSeconds": elapsed, "cost": cost(data),
                                   "error": error, "errors": errors})
        print(f"{batch_dir.name} attempt {attempt}: {len(assigned)} cues, {len(errors)} failed, USD {cost(data)}",
              flush=True)
        if not errors or status in (401, 402, 403):
            break
    failed = {c["id"]: errors.get(c["id"], ["Not attempted"]) for c in batch["assign"] if c["id"] not in accepted}
    save(batch_dir / "done.json", {"failed": failed})
    return spent


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--all", action="store_true", help="annotate every cue, not just missing or stale ones")
    parser.add_argument("--batch", type=int, default=30, help="cues per request")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--model", default=MODEL)
    args = parser.parse_args()
    title = app.title_id(args.document)

    manifest_path = args.run_dir / "manifest.json"
    if not manifest_path.exists():
        args.run_dir.mkdir(parents=True)
        raw = args.document.read_bytes()
        document = json.loads(raw)
        cues = cues_of(document)
        if app.problems(title, document):
            raise SystemExit("The document is invalid before annotation; fix it first: "
                             + "; ".join(app.problems(title, document)[:5]))
        guide = GUIDE.read_text(encoding="utf-8")
        (args.run_dir / "teaching-guide.md").write_text(guide, encoding="utf-8")
        (args.run_dir / "base-document.json").write_bytes(raw)
        manifest = {"model": args.model, "title": title, "documentSha256": hashlib.sha256(raw).hexdigest(),
                    "batches": []}
        for n, ids in enumerate(plan(document, args.all, args.batch), 1):
            batch_dir = args.run_dir / f"batch-{n:03}"
            batch_dir.mkdir()
            near = range(max(0, min(ids) - 8), min(len(cues), max(ids) + 9))
            save(batch_dir / "input.json", {
                "title": document["title"], "sourceLanguage": document["sourceLanguage"],
                "translationLanguage": document["translationLanguage"], "notesLanguage": document["notesLanguage"],
                "assign": [slim(cues[i]) for i in ids], "contextOnly": [slim(cues[i]) for i in near if i not in ids]})
            manifest["batches"].append(batch_dir.name)
        save(manifest_path, manifest)
    manifest = load(manifest_path)
    print(f"{len(manifest['batches'])} batches with {manifest['model']}", flush=True)

    guide = (args.run_dir / "teaching-guide.md").read_text(encoding="utf-8")
    base = load(args.run_dir / "base-document.json")

    def prompt(batch):
        return CONTRACT + guide + "\n\nINPUT:\n" + json.dumps(batch, ensure_ascii=False, indent=2)

    def one(name):
        return run(args.run_dir / name, prompt, manifest["model"], title, base)

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        spent = sum(pool.map(one, manifest["batches"]))

    sent, answers, failed = {}, {}, {}
    for name in manifest["batches"]:
        batch_dir = args.run_dir / name
        sent.update((c["id"], c["source"]) for c in load(batch_dir / "input.json")["assign"])
        answers.update(load(batch_dir / "accepted.json") if (batch_dir / "accepted.json").exists() else {})
        failed.update(load(batch_dir / "done.json")["failed"])
    document = load(args.document)
    applied, changed = [], []
    for cue in cues_of(document):
        study = cue["study"]
        answer = answers.get(study["id"])
        if answer is None:
            continue
        if study["source"] != sent[study["id"]]:
            changed.append(study["id"])
            continue
        annotate(study, answer)
        applied.append(study["id"])
    problems = app.problems(title, document)
    if problems:
        raise SystemExit("The annotated document is invalid; nothing was written: " + "; ".join(problems[:5]))
    save(args.run_dir / "document.json", document)
    temporary = args.document.with_name(args.document.name + ".tmp")
    save(temporary, document)
    temporary.replace(args.document)
    print(f"Applied {len(applied)} of {len(sent)} cues; this run cost USD {spent}; updated {args.document}")
    if changed:
        print(f"Skipped {len(changed)} cues whose source changed since generation: {', '.join(changed[:10])}")

    if not (args.run_dir / "queued.json").exists():
        model = manifest["model"]
        requests = [{"cueId": i, "text": f"First-pass translation and notes from {model}: review them."}
                    for i in applied]
        requests += [{"cueId": i, "text": f"{model} could not annotate this cue: " + "; ".join(e)[:1800]}
                     for i, e in failed.items() if i not in changed]
        queued = app.request_review(title, requests)
        save(args.run_dir / "queued.json", requests)
        print(f"Queued {queued} cues for review ({len(failed)} without first-pass notes)")


if __name__ == "__main__":
    main()
