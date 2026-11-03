"""Batched first-pass generation shared by translate.py, phrases.py and notes.py.

A run directory holds a manifest, the teaching guide and base document as sent, and one
directory per batch with its input, every attempt's request, response and result, the cues
accepted so far and a done marker. Rerunning the same command resumes from what is saved.
"""
import hashlib
import json
from concurrent.futures import ThreadPoolExecutor

import app
from openrouter import chat_json, cost, load, save

REPAIRS = 2


def cues_of(document):
    return (document.get("video") or document.get("comic"))["cues"]


def plan(document, wanted, size):
    """Batches of the cue indices WANTED, keeping comic pages together where they fit."""
    cues = cues_of(document)
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


def create(run_dir, document, raw, guide, model, title, wanted, size, slim):
    """Writes a new run directory: manifest, guide, base document and one input per batch.
    SLIM turns a cue into what the model sees; each batch carries up to eight neighbouring
    cues on either side as context only."""
    run_dir.mkdir(parents=True)
    cues = cues_of(document)
    (run_dir / "teaching-guide.md").write_text(guide, encoding="utf-8")
    (run_dir / "base-document.json").write_bytes(raw)
    manifest = {"model": model, "title": title, "documentSha256": hashlib.sha256(raw).hexdigest(),
                "batches": []}
    for n, ids in enumerate(plan(document, wanted, size), 1):
        batch_dir = run_dir / f"batch-{n:03}"
        batch_dir.mkdir()
        near = range(max(0, min(ids) - 8), min(len(cues), max(ids) + 9))
        save(batch_dir / "input.json", {
            "title": document["title"], "sourceLanguage": document["sourceLanguage"],
            "translationLanguage": document["translationLanguage"], "notesLanguage": document["notesLanguage"],
            "assign": [slim(cues[i]) for i in ids], "contextOnly": [slim(cues[i]) for i in near if i not in ids]})
        manifest["batches"].append(batch_dir.name)
    save(run_dir / "manifest.json", manifest)
    return manifest


def check(answer, assigned, title, base, shape_errors, annotate, lint=None):
    """Per-cue errors for one answer: {cue id: [messages]}; an empty list means accepted.
    SHAPE_ERRORS stops an answer being written into a document at all, ANNOTATE writes one
    into a cue, and the app's validator reports everything else. LINT, when given, adds
    advisory problems that are treated as errors for this attempt."""
    cues = answer.get("cues") if isinstance(answer, dict) else None
    given = {c.get("id"): c for c in cues if isinstance(c, dict)} if isinstance(cues, list) else {}
    errors = {c["id"]: (["Missing from the answer"] if c["id"] not in given else shape_errors(given[c["id"]]))
              for c in assigned}
    candidate = json.loads(json.dumps(base))
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
    if lint:
        for cue_id, problems in errors.items():
            if not problems:
                problems.extend(lint(given[cue_id]))
    return errors, given


def run(batch_dir, prompt, model, accept):
    """Generate one batch, accepting cues one by one and resending only failing cues, at most
    REPAIRS times. ACCEPT(answer, assigned, attempt) returns (errors, given) as check() does.
    Returns reported cost."""
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
            errors, given = accept(answer, assigned, attempt)
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


def run_all(run_dir, manifest, prompt, accept, workers):
    """Runs every batch. Returns (cost, {id: sent cue}, {id: accepted answer}, {id: errors})."""
    def one(name):
        return run(run_dir / name, prompt, manifest["model"], accept)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        spent = sum(pool.map(one, manifest["batches"]))
    sent, answers, failed = {}, {}, {}
    for name in manifest["batches"]:
        batch_dir = run_dir / name
        sent.update((c["id"], c) for c in load(batch_dir / "input.json")["assign"])
        answers.update(load(batch_dir / "accepted.json") if (batch_dir / "accepted.json").exists() else {})
        failed.update(load(batch_dir / "done.json")["failed"])
    return spent, sent, answers, failed


def write(document_path, run_dir, title, answers, unchanged, annotate):
    """Writes accepted ANSWERS into the document in place, skipping cues for which
    UNCHANGED(study) is false (edited since generation). Validates the whole document first
    and keeps a copy in the run directory. Returns (applied ids, changed ids)."""
    document = load(document_path)
    applied, changed = [], []
    for cue in cues_of(document):
        study = cue["study"]
        if study["id"] not in answers:
            continue
        if not unchanged(study):
            changed.append(study["id"])
            continue
        annotate(study, answers[study["id"]])
        applied.append(study["id"])
    problems = app.problems(title, document)
    if problems:
        raise SystemExit("The annotated document is invalid; nothing was written: " + "; ".join(problems[:5]))
    save(run_dir / "document.json", document)
    temporary = document_path.with_name(document_path.name + ".tmp")
    save(temporary, document)
    temporary.replace(document_path)
    return applied, changed


def queue(run_dir, title, requests):
    """Queues review requests once per run."""
    if (run_dir / "queued.json").exists():
        return None
    queued = app.request_review(title, requests)
    save(run_dir / "queued.json", requests)
    return queued
