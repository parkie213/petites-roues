import { test } from "vitest";
import assert from "node:assert/strict";
import { timingHit } from "../src/player/timing-edit.js";
const cues = [
  { id: "a", source: "A", start: 0, end: 2 },
  { id: "b", source: "B", start: 2, end: 4 },
];
test("adjoining start and end handles remain independently draggable without selecting a cue first", () => {
  assert.deepEqual(timingHit(cues, { x: 200, y: 28 }, 0, 100), {
    index: 0,
    mode: "end",
  });
  assert.deepEqual(timingHit(cues, { x: 200, y: 10 }, 0, 100), {
    index: 1,
    mode: "start",
  });
  assert.deepEqual(timingHit(cues, { x: 400, y: 28 }, 0, 100, 0), {
    index: 1,
    mode: "end",
  });
});
test("timing hit testing respects the zoomed viewport, body moves, and scrub area", () => {
  assert.deepEqual(timingHit(cues, { x: 50, y: 28 }, 1, 50), {
    index: 0,
    mode: "end",
  });
  assert.deepEqual(timingHit(cues, { x: 100, y: 10 }, 0, 100), {
    index: 0,
    mode: "move",
    offset: 1,
  });
  assert.equal(timingHit(cues, { x: 100, y: 70 }, 0, 100), null);
});

test("dragging the body of overlapping subtitles respects the selected cue", () => {
  const overlap = [
    { start: 1, end: 4 },
    { start: 2, end: 5 },
  ];
  assert.deepEqual(timingHit(overlap, { x: 300, y: 10 }, 0, 100, 1), {
    index: 1,
    mode: "move",
    offset: 1,
  });
});
