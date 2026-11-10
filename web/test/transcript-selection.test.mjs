import { test } from "vitest";
import assert from "node:assert/strict";
import { selectTranscriptRows } from "../src/player/transcript-selection.js";
test("modifier selection toggles without mutating the previous selection", () => {
  const selected = new Set(["a"]);
  assert.deepEqual(
    [...selectTranscriptRows(["a", "b"], selected, "a", "b", { toggle: true })],
    ["a", "b"],
  );
  assert.deepEqual(
    [...selectTranscriptRows(["a", "b"], selected, "a", "a", { toggle: true })],
    [],
  );
  assert.deepEqual([...selected], ["a"]);
});
test("shift selection works in both directions and only includes visible rows", () => {
  assert.deepEqual(
    [
      ...selectTranscriptRows(["a", "c", "d"], new Set(), "d", "a", {
        range: true,
      }),
    ],
    ["a", "c", "d"],
  );
  assert.deepEqual(
    [
      ...selectTranscriptRows(["a", "b", "c"], new Set(), "a", "c", {
        range: true,
      }),
    ],
    ["a", "b", "c"],
  );
});
test("ordinary clicks clear multi-selection and a missing range anchor toggles the clicked row", () => {
  assert.equal(
    selectTranscriptRows(["a", "b"], new Set(["a"]), "a", "b").size,
    0,
  );
  assert.deepEqual(
    [
      ...selectTranscriptRows(["a", "b"], new Set(), null, "b", {
        range: true,
        toggle: true,
      }),
    ],
    ["b"],
  );
});
