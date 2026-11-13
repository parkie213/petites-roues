"""Calls to the running Petites roues app, which owns validation and the review queue.

The app never writes document files, so these calls only check candidates and queue work for
the reviewing agent. Set PETITES_ROUES_URL if the app is not at http://localhost:8877.
"""
import json
import os
import urllib.error
import urllib.request
from urllib.parse import urlparse
from pathlib import Path

URL = os.environ.get("PETITES_ROUES_URL", "http://localhost:8877").rstrip("/")


def call(method, path, body=None, *, with_headers=False):
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
            value = json.loads(raw) if raw.strip() else None
            return (value, response.headers) if with_headers else value
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
    if not requests:
        return 0
    import uuid
    _, headers = call("GET", f"/api/titles/{title}", with_headers=True)
    version = headers["X-State-Version"]
    call("POST", f"/api/titles/{title}/commands", {
        "requestId": str(uuid.uuid4()), "expectedVersion": version,
        "commands": [{"type": "review-request", **request} for request in requests],
    })
    return len(requests)
