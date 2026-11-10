"""Shared OpenRouter request and run-directory helpers for the preparation scripts.

Reads the API key from OPENROUTER_API_KEY or ~/.config/openrouter/env. Every request and
raw response is saved in the run directory so a result can be traced to exactly what was sent; the key is never saved.
"""
import json
import os
import shlex
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://openrouter.ai/api/v1/"


def save(path, data):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


RETRY_WAITS = (10, 30, 90, 240)


def api_key():
    """Environment first, then the user's OpenRouter config; never execute config as shell.

    Only a literal OPENROUTER_API_KEY assignment is read from env.
    No repository .env files are searched or loaded.
    """
    if os.environ.get("OPENROUTER_API_KEY", "").strip():
        return os.environ["OPENROUTER_API_KEY"].strip()
    config = Path.home() / ".config" / "openrouter"
    env = config / "env"
    if env.is_file():
        for line in env.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("export "):
                line = line[7:].lstrip()
            name, sep, value = line.partition("=")
            if sep and name.strip() == "OPENROUTER_API_KEY":
                values = shlex.split(value, comments=True)
                if len(values) == 1 and values[0].strip():
                    return values[0].strip()
    raise SystemExit("Set OPENROUTER_API_KEY or configure ~/.config/openrouter/env")


def post(endpoint, body, timeout=300):
    """POST a JSON body. Returns (HTTP status, response JSON, elapsed seconds); errors become
    {"error": ...} with status 0 for network failures, so callers can record and retry.
    Rate limits, server errors and network failures are retried with backoff first."""
    key = api_key()
    request = urllib.request.Request(API + endpoint, data=json.dumps(body).encode(),
                                     headers={"Authorization": "Bearer " + key,
                                              "Content-Type": "application/json"})
    start = time.monotonic()
    for wait in RETRY_WAITS + (None,):
        retry_after = None
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                status, data = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, data = error.code, {"error": error.read().decode(errors="replace")}
            retry_after = error.headers.get("Retry-After")
        except Exception as error:
            status, data = 0, {"error": str(error)}
        if wait is None or not (status == 0 or status == 429 or status >= 500):
            break
        wait = max(wait, int(retry_after)) if (retry_after or "").isdigit() else wait
        print(f"{endpoint}: HTTP {status}, retrying in {wait}s", flush=True)
        time.sleep(wait)
    return status, data, round(time.monotonic() - start, 2)


def chat_json(body):
    """One chat completion whose answer is JSON. Returns (status, raw response, elapsed,
    parsed answer or None, error message or None)."""
    status, data, elapsed = post("chat/completions", body)
    try:
        choice = data["choices"][0]
        text = choice["message"]["content"].strip()
        if text.startswith("```"):
            text = text.split("\n", 1)[1].rsplit("```", 1)[0].strip()
        parsed = json.loads(text)
        if choice.get("finish_reason") != "stop":
            return status, data, elapsed, parsed, "Output did not finish: " + str(choice.get("finish_reason"))
        return status, data, elapsed, parsed, None
    except Exception as error:
        return status, data, elapsed, None, "Unusable response: " + str(data.get("error") or error)


def cost(data):
    return (data.get("usage") or {}).get("cost") or 0
