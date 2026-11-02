"""Calls to the running Petites roues app, which owns validation and the review queue.

The app never writes document files, so these calls only check candidates and queue work for
the reviewing agent. Set PETITES_ROUES_URL if the app is not at http://localhost:8877.
"""
import json
import os
import urllib.error
import urllib.request
from pathlib import Path

URL = os.environ.get("PETITES_ROUES_URL", "http://localhost:8877").rstrip("/")


def call(method, path, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    request = urllib.request.Request(URL + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.load(response)
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
