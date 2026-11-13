#!/usr/bin/env python3
"""Build the landing page and actual app with canned demo API data and saved audio."""
import json
import hashlib
from pathlib import Path
import shutil
import subprocess
import zipfile

from demo_audio import AUDIO, spoken_texts

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "site/dist"
if OUTPUT.exists():
    shutil.rmtree(OUTPUT)
OUTPUT.mkdir(parents=True)
for name in ("index.html", "favicon.svg"):
    shutil.copyfile(ROOT / "site" / name, OUTPUT / name)
# Build the actual app entry point; demo mode is selected by the HTML at runtime.
subprocess.run(["npm", "run", "build", "--", "--base", "./"], cwd=ROOT / "web", check=True)
shutil.copytree(ROOT / "web/dist", OUTPUT / "demo")
index = OUTPUT / "demo/index.html"
index.write_text(index.read_text().replace('<html lang="en">', '<html lang="en" data-demo="true">').replace('href="/favicon.png', 'href="./favicon.png'))
shutil.copyfile(ROOT / "web/src/demo/demo-worker.js", OUTPUT / "demo/demo-worker.js")
document = json.loads((ROOT / "example/documents-fr/comics/lulu/le-parapluie-de-lulu/document.json").read_text())
pages = OUTPUT / "demo/pages"
pages.mkdir()
with zipfile.ZipFile(ROOT / "example/media-fr/comics/lulu/le-parapluie-de-lulu.cbz") as archive:
    for page in document["comic"]["pages"]:
        (pages / (page["id"] + ".jpg")).write_bytes(archive.read(page["entry"]))
manifest = json.loads((AUDIO / "manifest.json").read_text())
if manifest["sourceLanguage"] != document["sourceLanguage"]:
    raise SystemExit("Demo recordings have the wrong source language; regenerate them.")
audio = OUTPUT / "demo/audio"
audio.mkdir()
for text in spoken_texts(document):
    name = manifest["clips"].get(text)
    if not name or Path(name).name != name or not (AUDIO / name).is_file():
        raise SystemExit(f"Missing demo recording for {text!r}; run site/demo_audio.py.")
    shutil.copyfile(AUDIO / name, audio / name)
(OUTPUT / "demo/demo-data.json").write_text(json.dumps({"document": document, "recordings": manifest}, ensure_ascii=False))
# Data changes also change the worker version, so an old in-memory sample is not kept forever.
worker = OUTPUT / "demo/demo-worker.js"
version = hashlib.sha256((OUTPUT / "demo/demo-data.json").read_bytes()).hexdigest()
worker.write_text(worker.read_text() + f"\n// Demo data version: {version}\n")
print(f"Built {OUTPUT}")
