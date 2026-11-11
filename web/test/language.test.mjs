import { test } from "vitest";
import assert from "node:assert/strict";
import { languagesIn, nativeName } from "../src/library/language.ts";

const repos = [
  {
    id: "docs",
    entries: [
      { id: "peppa", sourceLanguage: "fr" },
      { id: "masha", sourceLanguage: "de" },
      { id: "lunch", sourceLanguage: "zh-Hans" },
    ],
    media: [],
  },
  {
    id: "media",
    entries: [],
    media: [
      { relativePath: "peppa.mp4", titleId: "peppa" },
      { relativePath: "stray.mp4", titleId: null },
    ],
  },
];

test("languages are named in themselves and listed once each", () => {
  assert.equal(nativeName("fr"), "Français");
  assert.equal(nativeName("de"), "Deutsch");
  assert.deepEqual(
    languagesIn(repos).map((item) => item.tag),
    ["de", "fr", "zh-Hans"],
  );
});
