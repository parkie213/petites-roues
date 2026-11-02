import { test } from "node:test";
import assert from "node:assert/strict";
import {
  adjacentView,
  chapterAt,
  chapterName,
  pageStep,
  resumePage,
  spreadPages,
} from "../src/comic/navigation.js";

test("reading direction changes physical arrow keys, not manifest order", () => {
  assert.equal(pageStep("ArrowLeft", "rtl"), 1);
  assert.equal(pageStep("ArrowRight", "rtl"), -1);
  assert.equal(pageStep("ArrowLeft", "ltr"), -1);
  assert.equal(pageStep("ArrowRight", "ltr"), 1);
  assert.equal(pageStep("Space", "rtl"), 0);
});
test("resume uses stable page identity and falls back safely after manifest changes", () => {
  const pages = [{ id: "cover" }, { id: "story" }];
  assert.equal(resumePage(pages, "story"), 1);
  assert.equal(resumePage([...pages].reverse(), "story"), 0);
  assert.equal(resumePage(pages, "missing"), 0);
  assert.equal(resumePage(pages, null), 0);
});
test("spreads keep the cover alone and pair later pages", () => {
  assert.deepEqual(spreadPages(0, 6, true), [0]);
  assert.deepEqual(spreadPages(1, 6, true), [1, 2]);
  assert.deepEqual(spreadPages(2, 6, true), [1, 2]);
  assert.deepEqual(spreadPages(5, 6, true), [5]);
  assert.deepEqual(spreadPages(2, 6, false), [2]);
});
test("adjacent views step by whole spreads and stop at the ends", () => {
  assert.equal(adjacentView(0, 6, true, 1), 1);
  assert.equal(adjacentView(2, 6, true, 1), 3);
  assert.equal(adjacentView(2, 6, true, -1), 0);
  assert.equal(adjacentView(5, 6, true, 1), null);
  assert.equal(adjacentView(0, 6, true, -1), null);
  assert.equal(adjacentView(3, 6, false, -1), 2);
});

test("a chapter runs from its first page until the next one starts", () => {
  const pages = ["cover", "toc", "a1", "a2", "b1", "bonus"].map((id) => ({
    id,
  }));
  const chapters = [
    { number: 1, title: "Cruauté", pageId: "a1" },
    { number: 2, title: "Inconnu", pageId: "b1" },
    { title: "Bonus", pageId: "bonus" },
  ];
  assert.deepEqual(
    pages.map((_, i) => chapterAt(pages, chapters, i)),
    [-1, -1, 0, 0, 1, 2],
  );
  assert.equal(chapterAt(pages, undefined, 3), -1);
  assert.equal(chapterName(chapters[1]), "2 · Inconnu");
  assert.equal(chapterName(chapters[2]), "Bonus");
});
