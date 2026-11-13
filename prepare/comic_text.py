#!/usr/bin/env python3
"""Find and transcribe the text on a registered comic's pages with an OpenRouter vision model.

  comic_text.py --document content/my-comic/document.json --run-dir DIR [--media FILE] [--pages 9-11]

Reads the CBZ given by --media, else the one beside the document. Pages that already have boxes
are left alone. Several pages go into each request; every request and response is saved per
batch in the run directory, and rerunning the same command resumes, skipping answered batches.
Adds one box per text region to the document in place.

The model also flags pages that open a chapter and copies any table of contents. Once the run
directory holds answers for every page, the chapters are written to `comic.chapters`, but only
when they are consistent: numbers in order, and matching the table of contents (same chapters,
one page offset) when there is one. Otherwise the problems are printed for a by-eye check, and
the document's chapters are left alone; existing chapters are never replaced.
"""
import argparse
import base64
import uuid
import zipfile
from pathlib import Path

from openrouter import chat_json, cost, load, save

MODEL = "openai/gpt-6.1-terra"
IMAGES = (".jpg", ".jpeg", ".png", ".webp")
PROMPT = """Extract visible text from each {language} comic page below, including dialogue, narration, captions and readable sound effects. Transcribe, do not translate or paraphrase. Copy each word exactly as printed, never as it should be spelled: keep deliberate misspellings and accents written out phonetically (a German saying "ADDENTZION" stays "ADDENTZION"). Preserve accents and punctuation; join typographic line breaks within a text block and remove line-wrap hyphens only. Never invent obscured text; use [?] for unreadable portions. Return one region per speech bubble or distinct text block, not per line. Use {order} reading order: panels {panels}, top-to-bottom, then regions within each panel. Boxes must tightly enclose the TEXT, including all lines, not the whole bubble. Coordinates are integers normalized to 0..1000 relative to that complete page image: [left, top, right, bottom]. Include only visible text in the source language {language}; no scene descriptions. Skip text in other languages or scripts, including untranslated Japanese lettering and sound effects in a translated comic. Do not describe or name foreign characters (for example "Japanese letter"), and never transliterate them or invent source-language replacements. Keep source-language sound effects and deliberately accented source-language dialogue. Skip page numbers, printer's codes, artists' signatures and scan credits.
Also, separately from the regions: when a page opens a chapter (it shows a chapter heading such as "Chapitre 3 - Title"), set "chapterStart" to its number (null for an unnumbered section) and its title without the "Chapitre N" prefix; otherwise "chapterStart" is null. A table of contents is not a chapter start: on that page copy every entry into "contents" with its number, title and printed page number; otherwise "contents" is []. Headings and contents lines are still transcribed as regions.
Each image is preceded by its page label. Apply the instructions to each page independently. Return JSON only:
{{"pages":[{{"page":"label","chapterStart":null,"contents":[],"regions":[{{"order":1,"kind":"dialogue|narration|sfx|other","box":[0,0,1000,1000],"text":"exact visible text","uncertain":false}}]}}]}}
Include every supplied page; a page without text has "regions":[]."""


def page_range(value, total):
    start, _, end = value.partition("-")
    first, last = int(start), int(end) if end else (total if "-" in value else int(start))
    if not 1 <= first <= last <= total:
        raise SystemExit(f"--pages must lie within 1-{total}")
    return range(first - 1, last)


def to_box(region):
    left, top, right, bottom = (min(max(v / 1000, 0.0), 1.0) for v in region["box"])
    if right <= left or bottom <= top:
        raise ValueError("empty box")
    line = {"id": str(uuid.uuid4()), "source": region.get("text", "").strip()}
    if region.get("uncertain") or "[?]" in line["source"]:
        line["textCheck"] = "Model marked this text uncertain; check the printed page."
    return {"id": str(uuid.uuid4()),
            "regions": [{"x": left, "y": top, "width": right - left, "height": bottom - top}],
            "lines": [line]}


def chapters_from(pages, answers):
    """Chapters from the per-page answers (page id -> answer), with a list of problems that
    should stop them being written. A heading repeated on the next pages (a colour page, then
    the title page) counts once, at its first page."""
    starts = []
    for i, page in enumerate(pages):
        start = (answers.get(page["id"]) or {}).get("chapterStart") or {}
        title = str(start.get("title") or "").strip()
        if not title:
            continue
        number = start.get("number") if isinstance(start.get("number"), int) and start["number"] > 0 else None
        if starts and number is not None and starts[-1]["number"] == number:
            continue
        starts.append({"number": number, "title": title, "pageId": page["id"], "position": i})
    contents = {e["number"]: e for page in pages for e in (answers.get(page["id"]) or {}).get("contents") or []
                if isinstance(e.get("number"), int) and isinstance(e.get("printedPage"), int)}
    problems = []
    numbers = [s["number"] for s in starts if s["number"] is not None]
    if numbers != sorted(set(numbers)):
        problems.append(f"chapter numbers out of order or repeated: {numbers}")
    if contents:
        missing = sorted(set(contents) - set(numbers))
        extra = sorted(set(numbers) - set(contents))
        if missing:
            problems.append(f"in the table of contents but no heading found: {missing}")
        if extra:
            problems.append(f"headings not in the table of contents: {extra}")
        offsets = {s["number"]: s["position"] - contents[s["number"]]["printedPage"]
                   for s in starts if s["number"] in contents}
        if len(set(offsets.values())) > 1:
            problems.append(f"page offsets differ (archive position - printed page): {offsets}")
        for s in starts:
            if s["number"] in contents and str(contents[s["number"]].get("title") or "").strip():
                s["title"] = contents[s["number"]]["title"].strip()  # the contents line is cleaner
    if not starts:
        problems.append("no chapter headings found")
    chapters = [{k: v for k, v in s.items() if k != "position" and v is not None} for s in starts]
    return chapters, problems


def write_chapters(comic, pages, answers):
    """Writes consistent chapters after the pages; reports why it did not otherwise."""
    if comic.get("chapters"):
        return "Chapters: the document already lists them; left unchanged."
    unanswered = [p["id"] for p in pages if p["id"] not in answers]
    if unanswered:
        return f"Chapters: not written yet, {len(unanswered)} pages have no answer in this run directory."
    chapters, problems = chapters_from(pages, answers)
    if problems:
        found = ", ".join(f"{c.get('number', '-')} {c['pageId']}" for c in chapters)
        return "Chapters: not written; check by eye. " + "; ".join(problems) + f". Found: {found or 'none'}"
    comic["chapters"] = chapters
    return "Chapters: wrote " + ", ".join(f"{c.get('number', '-')} {c['pageId']} {c['title']}" for c in chapters)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--media", type=Path, help="the CBZ; defaults to the one beside the document")
    parser.add_argument("--pages", help="one-based positions: 9, 9-11 or 18-")
    parser.add_argument("--batch", type=int, default=3, help="pages per request")
    parser.add_argument("--model", default=MODEL)
    args = parser.parse_args()

    books = [args.media] if args.media else list(args.document.parent.glob("*.cbz"))
    if len(books) != 1:
        raise SystemExit(f"Expected one .cbz beside the document, found {len(books)}")
    book = zipfile.ZipFile(books[0])
    args.run_dir.mkdir(parents=True, exist_ok=True)
    document = load(args.document)
    comic = document["comic"]
    done = {p["id"] for p in comic["pages"] if p.get("boxes")}
    positions = page_range(args.pages, len(comic["pages"])) if args.pages else range(len(comic["pages"]))
    todo = [comic["pages"][i] for i in positions if comic["pages"][i]["id"] not in done]
    rtl = comic.get("readingDirection") == "rtl"
    prompt = PROMPT.format(language=document["sourceLanguage"], order="manga (right-to-left)" if rtl else "left-to-right",
                           panels="right-to-left" if rtl else "left-to-right")

    total = 0
    for b in range(0, len(todo), args.batch):
        pages = todo[b:b + args.batch]
        out = args.run_dir / f"batch-{pages[0]['id']}"
        if (out / "parsed.json").exists():
            continue
        out.mkdir(exist_ok=True)
        content = [{"type": "text", "text": prompt}]
        for page in pages:
            mime = "jpeg" if page["entry"].lower().endswith("jpg") else page["entry"].rsplit(".", 1)[1].lower()
            image = base64.b64encode(book.read(page["entry"])).decode()
            content += [{"type": "text", "text": f"Page {page['id']}:"},
                        {"type": "image_url", "image_url": {"url": f"data:image/{mime};base64,{image}"}}]
        settings = {"model": args.model, "temperature": 0, "max_tokens": 12000}
        save(out / "request.json", dict(settings, prompt=prompt, pages=[p["id"] for p in pages]))
        status, data, elapsed, parsed, error = chat_json(dict(settings, messages=[{"role": "user", "content": content}]))
        save(out / "response.json", data)
        total += cost(data)
        answered = {str(p.get("page")) for p in (parsed or {}).get("pages", [])}
        if not error and not {p["id"] for p in pages} <= answered:
            error = "Missing pages in answer"
        print(f"{out.name}: HTTP {status}, {elapsed}s, USD {cost(data)}" + (f", FAILED: {error}" if error else ""))
        if not error:
            save(out / "parsed.json", parsed)

    replies = {str(p.get("page")): p for f in args.run_dir.glob("batch-*/parsed.json") for p in load(f)["pages"]}
    answers = {page: answer.get("regions") or [] for page, answer in replies.items()}
    failed = [p["id"] for p in todo if p["id"] not in answers]
    document = load(args.document)  # pick up anything written while the requests ran
    comic = document["comic"]
    done = {p["id"] for p in comic["pages"] if p.get("boxes")}
    added, skipped = 0, []
    for page in comic["pages"]:
        if page["id"] in done or page["id"] not in answers:
            continue
        regions = sorted(answers[page["id"]], key=lambda r: r.get("order", 0))
        for region in (r for r in regions if r.get("text", "").strip()):
            try:
                page.setdefault("boxes", []).append(to_box(region))
                added += 1
            except (KeyError, TypeError, ValueError) as e:
                skipped.append(f"{page['id']} region {region.get('order')}: {e}")
    chapters = write_chapters(comic, comic["pages"], replies)
    temporary = args.document.with_name(args.document.name + ".tmp")
    save(temporary, document)
    temporary.replace(args.document)
    print(f"Added {added} boxes; this run cost USD {total}; updated {args.document}")
    print(chapters)
    for line in skipped:
        print("Skipped", line)
    if failed:
        raise SystemExit(f"No answer yet for {len(failed)} pages ({', '.join(failed[:5])}...); rerun to retry")


if __name__ == "__main__":
    main()
