import test from "node:test";
import assert from "node:assert/strict";
import {
  inLanguage,
  languagesIn,
  nativeName,
} from "../src/library/language.js";

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

test("choosing a language keeps its documents and drops media without one", () => {
  const french = inLanguage(repos, "fr");
  assert.deepEqual(
    french.flatMap((repo) => repo.entries.map((entry) => entry.id)),
    ["peppa"],
  );
  assert.deepEqual(
    french.flatMap((repo) => repo.media.map((item) => item.relativePath)),
    ["peppa.mp4"],
  );
  assert.equal(inLanguage(repos, null), repos, "no language shows everything");
});
