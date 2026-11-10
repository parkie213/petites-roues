import assert from "node:assert/strict";
import { test } from "vitest";
import {
  generalQuizBrief,
  generalQuizFileName,
  quizKey,
} from "../src/library/quizzes.js";

const quiz = {
  repository: "r1",
  repositoryName: "french-quizzes",
  id: "common-irregular-verbs",
  title: "Common irregular verbs",
  text: "# Common irregular verbs\n\nDrill être and avoir.\n\n## Traps\n\n- nous faisons\n",
};

test("a general brief carries the quiz text and the rules, headings nested", () => {
  const brief = generalQuizBrief(
    "# Subtitle quiz agent\n\nOne item at a time.",
    quiz,
  );
  assert.match(brief, /^# Quiz: Common irregular verbs\n/);
  assert.match(brief, /make up natural, varied sentences/);
  assert.match(
    brief,
    /## The quiz\n\n### Common irregular verbs\n\nDrill être and avoir\.\n\n#### Traps/,
  );
  assert.match(brief, /## Quiz rules\n\n.*ignore what they say about films/);
  assert.match(brief, /### Subtitle quiz agent\n\nOne item at a time\.\n$/);
  assert.ok(
    brief.indexOf("## The quiz") < brief.indexOf("## Quiz rules"),
    "the quiz comes before the generic rules",
  );
});

test("keys and file names", () => {
  assert.equal(quizKey(quiz), "r1/common-irregular-verbs");
  assert.equal(generalQuizFileName(quiz), "quiz-common-irregular-verbs.md");
});
