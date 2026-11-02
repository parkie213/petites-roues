import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  QUIZ_LINES,
  QUIZ_TYPES,
  quizBrief,
  quizFileName,
  quizType,
} from "../src/player/quiz.js";

const weather = {
  sourceText: "Il fait beau",
  translationText: "The weather is nice",
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
  const brief = build(cues, 0);
  assert.ok(brief.startsWith("# Subtitle quiz: Le Film"));
  assert.ok(
    brief.includes("### Subtitle quiz agent"),
    "bundled headings sit under the brief's sections",
  );
  assert.ok(!brief.includes("Teaching guide"), "the teaching guide stays out");
  assert.ok(brief.includes("`fr`"));
  assert.ok(brief.includes("## Lines just watched (0:10–0:12)"));
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

test("keeps only the last lines with source text", () => {
  const cues = Array.from({ length: 40 }, (_, i) =>
    at(i * 3, {
      source: i === 39 ? "" : `Ligne ${i}`,
      translation: `Line ${i}`,
    }),
  );
  const brief = build(cues, 39);
  assert.equal(brief.match(/^- \d+:\d\d /gm).length, QUIZ_LINES);
  assert.ok(brief.includes("Ligne 38 → Line 38"));
  assert.ok(!brief.includes("Ligne 8 "));
});

test("says so before the first line", () => {
  assert.ok(build([at(5, { source: "Bonjour" })], -1).includes("None yet"));
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
    0,
    type,
  );
  const at1 = brief.indexOf("## Quiz type: Verb tenses");
  assert.ok(at1 > 0 && at1 < brief.indexOf("## Quiz rules"));
  assert.ok(brief.includes("Ask for the tense or mood and the infinitive"));
});
