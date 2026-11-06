import test from "node:test";
import assert from "node:assert/strict";
import { askMessage, askTargets } from "../src/comic/ask.js";

const weather = {
  source: "Il fait beau",
  translation: "The weather is nice",
};
const alignment = {
  source: ["Il fait", "beau"],
  translation: ["The weather is", "nice"],
  links: [
    {
      source: [0],
      translation: [0],
      note: "`il fait` + adjective describes the weather",
    },
    { source: [1], translation: [1], note: "`beau` here means fine weather" },
  ],
};
const beau = {
  id: "a",
  pageId: "p1",
  order: 1,
  ...weather,
  notes: ["Weather uses faire."],
  alignment,
};
const other = {
  id: "b",
  pageId: "p1",
  order: 2,
  source: "On sort ?",
  translation: "Shall we go out?",
  notes: [],
};
const pending = { id: "c", pageId: "p1", order: 3, source: "", notes: [] };
// Boxes in the reader's flat view, each holding one of these lines.
const boxes = (...lines) =>
  lines.map((line) => ({
    id: `box-${line.id}`,
    pageId: line.pageId,
    order: line.order,
    regions: [],
    lines: [line],
  }));
const document = {
  title: "Le Livre",
  sourceLanguage: "fr",
  notesLanguage: "en",
  pages: [{ id: "p1", label: "12" }],
  boxes: boxes(pending, other, beau),
};

test("carries the question, bubble, notes and page context", () => {
  const message = askMessage(document, beau, 0, "  Why faire?  ");
  assert.ok(message.startsWith("I'm learning French by reading “Le Livre”"));
  assert.ok(message.includes("page 12"));
  assert.ok(message.includes("My question: Why faire?\n"));
  assert.ok(message.includes("I'm asking about the phrase “Il fait”."));
  assert.ok(message.includes("Bubble: Il fait beau"));
  assert.ok(message.includes("Translation: The weather is nice"));
  assert.ok(
    message.includes(
      "- “Il fait” → The weather is: `il fait` + adjective describes the weather",
    ),
  );
  assert.ok(message.includes("- Weather uses faire."));
  assert.ok(
    message.includes(
      "→ Il fait beau (The weather is nice)\n- On sort ? (Shall we go out?)",
    ),
    "page bubbles follow reading order, skipping ones without text",
  );
  assert.ok(message.includes("Please answer in English."));
});

test("a blank question asks for depth; a lone bubble has no context list", () => {
  const alone = { ...document, boxes: boxes(other) };
  const message = askMessage(alone, other, null, " ");
  assert.ok(message.includes("Please explain this bubble in more depth."));
  assert.ok(!message.includes("My question"));
  assert.ok(!message.includes("asking about the phrase"));
  assert.ok(!message.includes("for context"));
});

test("chat links carry the whole message", () => {
  const [chatgpt, claude] = askTargets;
  assert.equal(chatgpt.url("a b?"), "https://chatgpt.com/?q=a%20b%3F");
  assert.equal(claude.url("é"), "https://claude.ai/new?q=%C3%A9");
});
