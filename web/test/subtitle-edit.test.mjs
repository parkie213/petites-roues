import test from "node:test";
import assert from "node:assert/strict";
import {
  editClock,
  editSeconds,
  splitDraft,
} from "../src/player/subtitle-edit.js";

test("editor times retain millisecond precision across minutes and hours", () => {
  for (const seconds of [0, 59.999, 60, 928.24, 3600.001]) {
    assert.equal(editSeconds(editClock(seconds)), seconds);
  }
  assert.equal(editSeconds("1:02:03.456"), 3723.456);
  assert.equal(editSeconds("928.240"), 928.24);
  for (const invalid of ["", "1:70", "abc", "-1", "1:2", "1.0009"])
    assert.equal(editSeconds(invalid), null);
});
test("splitting requires real text on both sides and a strictly interior playhead", () => {
  const draft = { start: 10, end: 12, source: "Bonjour tout le monde." };
  assert.deepEqual(splitDraft(draft, 8, 11), {
    start: 10,
    end: 12,
    at: 11,
    first: "Bonjour",
    second: "tout le monde.",
  });
  for (const cursor of [0, draft.source.length])
    assert.throws(() => splitDraft(draft, cursor, 11), /cursor/);
  for (const at of [9, 10, 12, 13])
    assert.throws(() => splitDraft(draft, 8, at), /split time/);
});
