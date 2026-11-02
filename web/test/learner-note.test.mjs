import test from "node:test";
import assert from "node:assert/strict";
import { noteSegments } from "../src/player/learner-note.js";

test("arbitrary French constructions can be marked without a UI dictionary", () => {
  assert.deepEqual(
    noteSegments("Use `avoir beau + infinitive` for an unsuccessful effort."),
    [
      { text: "Use ", highlighted: false },
      { text: "avoir beau + infinitive", highlighted: true },
      { text: " for an unsuccessful effort.", highlighted: false },
    ],
  );
});

test("multiple French spans retain punctuation and unmatched markup stays literal", () => {
  assert.deepEqual(noteSegments("`Veut` → `vouloir`; `unfinished"), [
    { text: "Veut", highlighted: true },
    { text: " → ", highlighted: false },
    { text: "vouloir", highlighted: true },
    { text: "; `unfinished", highlighted: false },
  ]);
  assert.deepEqual(noteSegments("<script>literal</script>"), [
    { text: "<script>literal</script>", highlighted: false },
  ]);
});
