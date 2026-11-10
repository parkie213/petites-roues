import { test } from "vitest";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  parseQuizTime,
  QUIZ_TYPES,
  quizBrief,
  quizFileName,
  quizType,
} from "../src/player/quiz.js";

const weather = {
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
const doc = {
  title: "Le Film",
  sourceLanguage: "fr",
  translationLanguage: "en",
  notesLanguage: "en",
};
const at = (start, fields) => ({ start, end: start + 2, notes: [], ...fields });
const build = (cues, current) =>
  quizBrief("# Subtitle quiz agent\n\nRules.", doc, cues, current);

test("carries the rules and watched lines with their translations", () => {
  const cues = [
    at(10, {
      source: "Il fait beau",
      translation: "The weather is nice",
      notes: ["Weather uses faire."],
      alignment: weather,
    }),
    at(70, { source: "Pas encore vu", translation: "Not seen yet" }),
  ];
  const brief = build(cues, { start: 0, end: 30 });
  assert.ok(brief.startsWith("# Subtitle quiz: Le Film"));
  assert.ok(
    brief.includes("### Subtitle quiz agent"),
    "bundled headings sit under the brief's sections",
  );
  assert.ok(!brief.includes("Teaching guide"), "the teaching guide stays out");
  assert.ok(brief.includes("`fr`"));
  assert.ok(brief.includes("## Lines in time range (0:00–0:30)"));
  assert.ok(brief.includes("\n- 0:10 Il fait beau → The weather is nice\n"));
  assert.ok(
    !brief.includes("adjective describes") &&
      !brief.includes("Weather uses faire"),
    "learner notes are left out: the quiz only needs the lines",
  );
  assert.ok(
    !brief.includes("Pas encore vu"),
    "lines after the current one are not included",
  );
});

test("whole episode includes all source lines without a line limit", () => {
  const cues = Array.from({ length: 45 }, (_, i) =>
    at(i * 3, { source: `Line ${i}` }),
  );
  cues.push(at(150, { source: " " }));
  const brief = build(cues, null);
  assert.equal(brief.match(/^- \d+:\d\d /gm).length, 45);
  assert.ok(brief.includes("## Whole episode"));
});

test("time range includes overlaps and excludes touching boundaries", () => {
  const brief = build(
    [
      at(8, { source: "Before" }),
      at(9, { source: "Overlaps start" }),
      at(15, { source: "Inside" }),
      at(19, { source: "Overlaps end" }),
      at(20, { source: "After" }),
    ],
    { start: 10, end: 20 },
  );
  for (const text of ["Overlaps start", "Inside", "Overlaps end"])
    assert.ok(brief.includes(text));
  for (const text of ["Before", "After"]) assert.ok(!brief.includes(text));
  assert.ok(build([], { start: 0, end: 10 }).includes("No subtitle lines"));
});

test("parses minute and hour ranges and rejects malformed times", () => {
  assert.equal(parseQuizTime("30:00"), 1800);
  assert.equal(parseQuizTime("1:02:03"), 3723);
  assert.equal(parseQuizTime("0:00"), 0);
  for (const value of [
    "",
    "-1:00",
    "1:60",
    "1:60:00",
    "abc",
    "1:2",
    "Infinity",
  ]) {
    assert.equal(parseQuizTime(value), null);
  }
});

test("names the file after the title", () => {
  assert.equal(
    quizFileName({ title: "Le Château ambulant !" }),
    "quiz-le-château-ambulant.md",
  );
  assert.equal(quizFileName({ title: "?!" }), "quiz-title.md");
});

const types = readFileSync(
  new URL("../../agents/workflows/quiz-types.md", import.meta.url),
  "utf8",
);

test("every quiz type has instructions in quiz-types.md", () => {
  for (const { id } of QUIZ_TYPES) {
    const type = quizType(types, id, doc);
    assert.ok(type.instructions.length > 40, id);
    assert.ok(
      !type.instructions.includes("## "),
      `${id} stops at the next type`,
    );
  }
  assert.equal(
    quizType(types, "to-source", doc).label,
    "Translate into French",
  );
  assert.equal(
    quizType(types, "to-translation", doc).label,
    "Translate into English",
  );
  assert.equal(quizType(types, "unknown", doc).id, "to-source");
});

test("the chosen type sits above the rules", () => {
  const type = quizType(types, "tenses", doc);
  const brief = quizBrief(
    "# Rules",
    doc,
    [at(1, { source: "Il est parti" })],
    null,
    type,
  );
  const at1 = brief.indexOf("## Quiz type: Verb tenses");
  assert.ok(at1 > 0 && at1 < brief.indexOf("## Quiz rules"));
  assert.ok(brief.includes("Ask for the tense or mood and the infinitive"));
});
