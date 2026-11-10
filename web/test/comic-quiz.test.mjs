import { test } from "vitest";
import assert from "node:assert/strict";
import {
  chapterPages,
  chapterQuizBrief,
  chapterQuizFileName,
} from "../src/comic/quiz.js";

// A box holding one line, in the reader's flat view.
const bubble = (pageId, order, source, fields = {}) => ({
  id: `${pageId}-${order}`,
  pageId,
  order,
  regions: [],
  lines: [
    { id: `${pageId}-${order}-line`, pageId, source, notes: [], ...fields },
  ],
});
const doc = {
  title: "Demon Slayer — Tome 1",
  sourceLanguage: "fr",
  translationLanguage: "en",
  notesLanguage: "en",
  pages: ["cover", "a1", "a2", "b1"].map((id) => ({ id })),
  chapters: [
    { number: 1, title: "Cruauté", pageId: "a1" },
    { number: 2, title: "Inconnu", pageId: "b1" },
  ],
  boxes: [
    bubble("a2", 2, "Je te sauverai !", { translation: "I'll save you!" }),
    bubble("a2", 1, "Nezuko…", { notes: ["A name called out."] }),
    bubble("a1", 1, "C'est un cauchemar !"),
    bubble("a1", 2, " "),
    bubble("cover", 1, "Demon Slayer"),
    bubble("b1", 1, "Excusez-moi…"),
  ],
};
const brief = (index) =>
  chapterQuizBrief(
    "# Subtitle quiz agent\n\nRules.",

    doc,
    index,
  );

test("a chapter runs to the page before the next one, or to the end", () => {
  assert.deepEqual(chapterPages(doc, 0), { first: 1, last: 2 });
  assert.deepEqual(chapterPages(doc, 1), { first: 3, last: 3 });
});

test("carries the chapter's bubbles in reading order with their translations", () => {
  const text = brief(0);
  assert.match(
    text,
    /^# Chapter quiz: Demon Slayer — Tome 1, chapter 1 · Cruauté\n/,
  );
  assert.match(text, /a bubble, and the film means this comic/);
  assert.match(text, /## Bubbles in chapter 1 · Cruauté \(pages 2–3\)/);
  const order = ["C'est un cauchemar !", "Nezuko…", "Je te sauverai !"].map(
    (s) => text.indexOf(s),
  );
  assert.ok(
    order.every((at, i) => at > 0 && (i === 0 || at > order[i - 1])),
    order,
  );
  assert.match(
    text,
    /### Page 2\n\n- C'est un cauchemar !\n\n### Page 3\n\n- Nezuko…\n- Je te sauverai ! → I'll save you!\n/,
  );
  assert.doesNotMatch(text, /A name called out/);
  assert.match(
    text,
    /\(pages 2–3\)\n\nPick items from all over these lines, in a mixed order/,
  );
  assert.doesNotMatch(text, /Demon Slayer\n|Excusez-moi/);
  assert.match(text, /### Rules\.|Rules\./);
  assert.doesNotMatch(text, /Teaching guide/);
});

test("names the file after the chapter", () => {
  assert.equal(
    chapterQuizFileName(doc, 1),
    "quiz-demon-slayer-tome-1-chapter-2.md",
  );
  const bonus = { ...doc, chapters: [{ title: "Bonus", pageId: "b1" }] };
  assert.equal(
    chapterQuizFileName(bonus, 0),
    "quiz-demon-slayer-tome-1-bonus.md",
  );
});
