import { test } from "vitest";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { QUIZ_TYPES, quizFileName, quizType } from "../src/player/quiz.js";

const doc = {
  title: "Le Film",
  sourceLanguage: "fr",
  translationLanguage: "en",
  notesLanguage: "en",
};

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
