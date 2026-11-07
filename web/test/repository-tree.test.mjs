import test from "node:test";
import assert from "node:assert/strict";
import {
  repositoryTree,
  repositoryColumns,
} from "../src/library/repository-tree.js";

test("repository folders preserve nesting, numeric ordering and multiple editions", () => {
  const tree = repositoryTree([
    {
      id: "ten",
      title: "Episode ten",
      relativePath: "show/season-01/episode-10/document.json",
    },
    {
      id: "two",
      title: "Episode two",
      relativePath: "show/season-01/episode-02/document.json",
    },
    { id: "film", title: "Film", relativePath: "film\\edition\\document.json" },
    { id: "root", title: "Root title", relativePath: "document.json" },
  ]);
  assert.equal(tree.titles[0].id, "root");
  assert.deepEqual(
    tree.folders.map((f) => f.name),
    ["film", "show"],
  );
  assert.equal(tree.folders[0].folders[0].titles[0].id, "film");
  assert.deepEqual(
    tree.folders[1].folders[0].folders.map((f) => f.titles[0].id),
    ["two", "ten"],
  );
});
test("column navigation keeps ancestors and stops at an invalid folder", () => {
  const entries = [
    {
      id: "a",
      title: "Episode A",
      relativePath: "show/season/episode/document.json",
    },
    { id: "b", title: "Episode B", relativePath: "other/document.json" },
  ];
  const columns = repositoryColumns(entries, ["show", "season", "episode"]);
  assert.deepEqual(
    columns.map((column) => column.parts),
    [[], ["show"], ["show", "season"], ["show", "season", "episode"]],
  );
  assert.equal(columns.at(-1).node.titles[0].id, "a");
  assert.equal(
    repositoryColumns(entries, ["show", "missing", "episode"]).length,
    2,
  );
  const branch = repositoryColumns(entries, ["other"]);
  assert.equal(branch.length, 2);
  assert.equal(branch.at(-1).node.titles[0].id, "b");
});
test("a title with linked media sits where the media lives, beside unlinked media", () => {
  const tree = repositoryTree(
    [
      {
        id: "lunch",
        title: "Lunch",
        relativePath: "peppa/s01e34/document.json",
        mediaPath: "peppa-pig/lunch.zh.mp4",
      },
    ],
    [
      {
        relativePath: "peppa-pig/stray.mkv",
        kind: "video",
        warnings: ["No study document"],
      },
    ],
  );
  assert.deepEqual(
    tree.folders.map((f) => f.name),
    ["peppa-pig"],
  );
  const [lunch, stray] = [...tree.folders[0].titles].sort((a, b) =>
    a.id.localeCompare(b.id),
  );
  assert.equal(stray.id, "media:peppa-pig/stray.mkv");
  assert.equal(stray.kind, "media");
  assert.equal(stray.mediaKind, "video");
  assert.equal(stray.title, "stray.mkv");
  assert.equal(lunch.id, "lunch");
});
