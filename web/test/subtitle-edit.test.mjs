import { test } from "vitest";
import assert from "node:assert/strict";
import {
  blankLine,
  editClock,
  editSeconds,
  lineAfter,
  lineChanged,
  linePayload,
  neighbourTimings,
  neighbours,
  splitDraft,
} from "../src/player/subtitle-edit.ts";

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

test("the split error names the text's language", () => {
  const draft = { start: 10, end: 12, source: "Guten Tag." };
  assert.throws(
    () => splitDraft(draft, 0, 11, "German"),
    /Place the German text cursor/,
  );
});

test("a new line at the playhead lasts two seconds, unless the next line or the end comes first", () => {
  const cues = [
    { start: 1, end: 3 },
    { start: 8, end: 10 },
  ];
  assert.deepEqual(blankLine(cues, 4, 60), {
    start: 4,
    end: 6,
    source: "",
    translation: "",
    notes: [],
  });
  assert.equal(blankLine(cues, 7, 60).end, 8);
  assert.equal(blankLine(cues, 59, 59.5).end, 59.5);
  // A video whose length isn't known yet doesn't cut it short.
  assert.equal(blankLine([], 4, NaN).end, 6);
});

test("a line has changed when its text, translation or times have", () => {
  const original = { start: 1, end: 2, source: "Oui.", translation: null };
  const times = { start: 1, end: 2 };
  assert.ok(!lineChanged(original, { ...original }, times));
  // No translation and an empty one are the same.
  assert.ok(!lineChanged(original, { ...original, translation: "" }, times));
  assert.ok(lineChanged(original, { ...original, source: "Non." }, times));
  assert.ok(lineChanged(original, { ...original, translation: "Yes." }, times));
  assert.ok(lineChanged(original, original, { start: 1, end: 2.5 }));
});

test("a saved line is trimmed, with its empty fields made explicit", () => {
  assert.deepEqual(
    linePayload(
      { id: "b", source: "  Oui. ", translation: "  ", start: 0, end: 1 },
      { start: 3, end: 4 },
    ),
    {
      id: "b",
      source: "Oui.",
      translation: null,
      start: 3,
      end: 4,
      notes: [],
      continuesPrevious: false,
      listeningCheck: null,
      textCheck: null,
    },
  );
});

test("after a split the first part is edited, and after a delete the line that follows", () => {
  const split = [
    { id: "a", start: 1, end: 2.5 },
    { id: "b-1", start: 2.5, end: 3 },
    { id: "b-2", start: 3, end: 4 },
  ];
  assert.equal(lineAfter("split", lines, lines[1], split).id, "b-1");
  const deleted = [lines[0], lines[2]];
  assert.equal(lineAfter("delete", lines, lines[1], deleted).id, "c");
  // The last line's place is empty, so the one before it.
  assert.equal(lineAfter("delete", lines, lines[2], lines.slice(0, 2)).id, "b");
  assert.equal(lineAfter("delete", [lines[0]], lines[0], []), null);
});
