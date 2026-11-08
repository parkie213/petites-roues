import test from "node:test";
import assert from "node:assert/strict";
import {
  documentChoices,
  filterChoices,
  mediaChoices,
} from "../src/library/media-links.js";

const repos = [
  {
    id: "docs",
    name: "documents-fr",
    entries: [
      {
        id: "e1",
        type: "video",
        title: "Les flaques de boue",
        series: "Peppa Pig",
        season: 1,
        episode: 1,
        documentAvailable: true,
        mediaAvailable: true,
        mediaPath: "s01e01.mp4",
        durationSeconds: 30,
      },
      {
        id: "e2",
        type: "video",
        title: "À la recherche de Mr Dinosaure",
        series: "Peppa Pig",
        season: 1,
        episode: 2,
        documentAvailable: true,
        mediaAvailable: false,
        durationSeconds: 31,
      },
      { id: "gone", type: "video", title: "Gone", documentAvailable: false },
      { id: "lulu", type: "comic", title: "Lulu", documentAvailable: true },
    ],
    media: [],
  },
  {
    id: "media",
    name: "media-fr",
    entries: [],
    media: [
      { relativePath: "s01e01.mp4", kind: "video", titleId: "e1" },
      { relativePath: "s01e02.mp4", kind: "video", titleId: null },
      { relativePath: "lulu.cbz", kind: "comic", titleId: "lulu" },
    ],
  },
];

test("documents of the media's kind, those without media first", () => {
  const choices = documentChoices(repos, "video");
  assert.deepEqual(
    choices.map((c) => c.titleId),
    ["e2", "e1"],
  );
  assert.equal(
    choices[0].detail,
    "Peppa Pig S1E2 · 0:31 · documents-fr · no media",
  );
  assert.match(choices[1].detail, /has s01e01\.mp4$/);
});

test("media of the document's kind, unlinked first, naming what the rest belong to", () => {
  const choices = mediaChoices(repos, "video");
  assert.deepEqual(
    choices.map((c) => c.relativePath),
    ["s01e02.mp4", "s01e01.mp4"],
  );
  assert.equal(choices[0].repositoryId, "media");
  assert.equal(choices[1].detail, "media-fr · linked to Les flaques de boue");
});

test("filtering needs every word, in label or detail", () => {
  const choices = documentChoices(repos, "video");
  assert.deepEqual(
    filterChoices(choices, "peppa E2").map((c) => c.titleId),
    ["e2"],
  );
  assert.equal(filterChoices(choices, "  ").length, 2);
});
