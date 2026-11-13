"""Calls to the running Petites roues app, which owns validation and the review queue.

The app never writes document files and serves an accepted copy of each, so a file a script has
written is unknown to the app until `import_title`. These calls check candidates, import files
and queue work for the reviewing agent. Set PETITES_ROUES_URL if the app is not at http://localhost:8877.
"""
import json
import os
import urllib.error
import urllib.request
from urllib.parse import urlparse
from pathlib import Path

URL = os.environ.get("PETITES_ROUES_URL", "http://localhost:8877").rstrip("/")


def call(method, path, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    request = urllib.request.Request(URL + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
    try:
        # Local app calls must not go through an HTTP proxy configured for internet traffic.
        opener = (urllib.request.build_opener(urllib.request.ProxyHandler({}))
                  if urlparse(URL).hostname in {"localhost", "127.0.0.1", "::1"}
                  else urllib.request.build_opener())
        with opener.open(request, timeout=120) as response:
            raw = response.read()
            return json.loads(raw) if raw.strip() else None
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{method} {path}: HTTP {error.code} {error.read().decode(errors='replace')}")
    except urllib.error.URLError as error:
        raise SystemExit(f"The app is not reachable at {URL} ({error.reason}); start it first")


def title_id(document):
    """The library ID of the title whose registered document is DOCUMENT."""
    wanted = Path(document).resolve()
    roots = {r["id"]: Path(r["localPath"]) for r in call("GET", "/api/repositories")}
    for title in call("GET", "/api/library/titles"):
        root = roots.get(title["repositoryId"])
        if root and (root / title["relativePath"]).resolve() == wanted:
            return title["id"]
    raise SystemExit(f"{document} is not a registered title; add it in the library first")


def problems(title, document):
    """Every structural problem in a candidate document; empty means the app would load it."""
    return call("POST", f"/api/titles/{title}/validate", document)["problems"]


def request_review(title, requests):
    """Queue [{"cueId", "text"}] for the reviewing agent. Returns how many were queued."""
    return call("POST", f"/api/titles/{title}/review/requests", requests)["queued"] if requests else 0


def require_no_open_batch(title):
    """Stop before rewriting a title's document while a review batch is open on it: that batch's
    completion is the title's import, and a rewrite now would collide with the agent's file."""
    for pending in call("GET", "/api/review/pending"):
        if pending["titleId"] == title and pending.get("openBatch") is not None:
            raise SystemExit(f"Review batch {pending['openBatch']} is open on this title; "
                             "complete it before rewriting the document")


def import_title(title):
    """Make the app serve the title's registered file just written. Returns "imported" or
    "unchanged"; stops with the server's message when the title has an open batch or its
    accepted copy changed meanwhile, and the same command can then be rerun."""
    try:
        return call("POST", f"/api/titles/{title}/import")["result"]
    except SystemExit as error:
        raise SystemExit(f"The file is written but not imported: {error}")
