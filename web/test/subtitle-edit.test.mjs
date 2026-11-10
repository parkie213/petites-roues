import { test } from "vitest";
import assert from "node:assert/strict";
import {
  editClock,
  editSeconds,
  neighbourTimings,
  neighbours,
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

const lines = [
  { id: "a", start: 1, end: 2.5 },
  { id: "b", start: 2.5, end: 4 },
  { id: "c", start: 4.24, end: 6 },
];
test("a line's neighbours are linked by default only where they meet it", () => {
  assert.deepEqual(neighbours(lines, lines[1]).touching, {
    previous: true,
    next: false,
  });
  assert.deepEqual(neighbours(lines, lines[0]), {
    previous: null,
    next: lines[1],
    touching: { previous: false, next: true },
  });
  assert.equal(neighbours(lines, lines[2]).next, null);
});
test("linked neighbours join the line's boundaries, closing any gap", () => {
  const both = { previous: true, next: true };
  assert.deepEqual(
    neighbourTimings(lines, lines[1], { start: 2.4, end: 4.1 }, both),
    [
      { id: "a", start: 1, end: 2.4 },
      { id: "c", start: 4.1, end: 6 },
    ],
  );
  assert.deepEqual(
    neighbourTimings(
      lines,
      lines[1],
      { start: 2.4, end: 4.1 },
      {
        previous: false,
        next: false,
      },
    ),
    [],
  );
  assert.deepEqual(
    neighbourTimings(lines, lines[1], { start: 2.5, end: 4.24 }, both),
    [],
  );
  assert.throws(
    () => neighbourTimings(lines, lines[1], { start: 1, end: 4 }, both),
    /previous line/,
  );
  assert.throws(
    () => neighbourTimings(lines, lines[1], { start: 2.5, end: 6 }, both),
    /next line/,
  );
});
