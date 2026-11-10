import assert from "node:assert/strict";
import { test } from "vitest";
import { findNote, noteGroups, notePath } from "../src/library/notes.js";

const note = (repository, id, repositoryName = repository) => ({
  repository,
  repositoryName,
  id,
  title: id,
  text: "",
});

test("note addresses round-trip through the decoded route parts", () => {
  const n = note("r 1", "passé-composé");
  assert.equal(notePath(n), "/notes/r%201/pass%C3%A9-compos%C3%A9");
  assert.equal(findNote([note("r 1", "x"), n], "r 1", "passé-composé"), n);
  assert.equal(findNote([n], "r 2", "passé-composé"), undefined);
});

test("notes are grouped by repository only when there are several", () => {
  assert.deepEqual(
    noteGroups([note("a", "x"), note("a", "y")]).map((g) => [
      g.name,
      g.notes.map((n) => n.id),
    ]),
    [[null, ["x", "y"]]],
  );
  assert.deepEqual(
    noteGroups([note("a", "x", "French"), note("b", "y", "Korean")]).map(
      (g) => [g.name, g.notes.map((n) => n.id)],
    ),
    [
      ["French", ["x"]],
      ["Korean", ["y"]],
    ],
  );
});
