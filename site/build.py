#!/usr/bin/env python3
"""Build the landing page and a static demo from the original sample comic; no server or recordings."""
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "site/dist"
if OUTPUT.exists():
    shutil.rmtree(OUTPUT)
OUTPUT.mkdir(parents=True)
for name in ("index.html", "favicon.svg"):
    shutil.copyfile(ROOT / "site" / name, OUTPUT / name)
subprocess.run(["npm", "run", "build:demo"], cwd=ROOT / "web", check=True)
document = json.loads((ROOT / "example/documents-fr/comics/lulu/le-parapluie-de-lulu/document.json").read_text())
pages = OUTPUT / "demo/pages"
pages.mkdir()
with zipfile.ZipFile(ROOT / "example/media-fr/comics/lulu/le-parapluie-de-lulu.cbz") as archive:
    for page in document["comic"]["pages"]:
        (pages / (page["id"] + ".jpg")).write_bytes(archive.read(page["entry"]))
print(f"Built {OUTPUT}")
