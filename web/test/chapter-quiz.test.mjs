import { readFileSync } from "node:fs";
import { expect, test } from "vitest";
import { chapterQuiz } from "../src/comic/chapter-quiz.js";

const document = JSON.parse(
  readFileSync(
    new URL(
      "../../example/documents-fr/comics/lulu/le-parapluie-de-lulu/document.json",
      import.meta.url,
    ),
  ),
);
document.comic.chapters = JSON.parse(
  readFileSync(new URL("../../example/demo/chapters.json", import.meta.url)),
);

test.each([0, 1])(
  "chapter %s exports only its source and translations, with the selected type and rules",
  (index) => {
    const chapter = document.comic.chapters[index];
    const type = {
      label: "Vocabulary",
      instructions: "Ask about the meaning in context.",
    };
    const result = chapterQuiz(document, {
      chapterPageId: chapter.pageId,
      rules: "# Rules\nWait for the learner's attempt.",
      type,
    });
    expect(result.text).toContain(`## Quiz type: ${type.label}`);
    expect(result.text).toContain(type.instructions);
    expect(result.text).toContain("Wait for the learner's attempt.");
    expect(result.text).toContain(
      "Source language: `fr`; translation language: `en`; notes language: `en`",
    );
    expect(result.fileName).toBe(
      `quiz-le-parapluie-de-lulu-chapter-${index + 1}.md`,
    );
    const pages = document.comic.pages;
    for (const [position, page] of pages.entries())
      for (const box of page.boxes ?? [])
        for (const line of box.lines) {
          const included = index === 0 ? position === 1 : position >= 2;
          expect(result.text.includes(`- ${line.source}`)).toBe(included);
          if (included) expect(result.text).toContain(` → ${line.translation}`);
          for (const note of line.notes ?? [])
            expect(result.text).not.toContain(note);
          for (const link of line.alignment?.links ?? [])
            if (link.note) expect(result.text).not.toContain(link.note);
        }
  },
);

test("unknown chapters and malformed requests cannot produce a quiz for the whole comic", () => {
  expect(() =>
    chapterQuiz(document, { chapterPageId: "missing", rules: "rules" }),
  ).toThrow("Unknown chapter");
  expect(() =>
    chapterQuiz(document, { chapterPageId: "page-1", rules: null }),
  ).toThrow("Quiz rules are required");
  expect(() =>
    chapterQuiz(document, {
      chapterPageId: "page-1",
      rules: "rules",
      type: { label: "Vocabulary" },
    }),
  ).toThrow("Invalid quiz type");
});
