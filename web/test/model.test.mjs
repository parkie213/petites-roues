import test from "node:test";
import assert from "node:assert/strict";
import {
  activeCues,
  currentCue,
  formatTime,
  insertGap,
  moveTiming,
} from "../src/player/model.js";

test("cue identity survives timing edits that overlap neighbours", () => {
  const cues = [
    { id: "a", start: 0, end: 1 },
    { id: "b", start: 2, end: 3 },
    { id: "c", start: 4, end: 5 },
  ];
  const changed = moveTiming(cues, 1, "start", 0.2);
  assert.equal(changed[1].start, 0.2);
  assert.equal(changed[1].id, "b");
  assert.equal(cues[1].start, 2);
  const moved = moveTiming(cues, 1, "move", 8, 0);
  assert.equal(moved[1].start, 8);
  assert.equal(moved[1].end - moved[1].start, 1);
  assert.equal(currentCue(cues, 2.5), 1);
  assert.equal(currentCue(cues, -1), -1);
  assert.equal(formatTime(3599.9996), "1:00:00.000");
});
test("inserting a practice gap never drops a filled answer", () => {
  assert.deepEqual(insertGap(["one", "two", ""], 1, 3), ["one", "", "two"]);
  assert.throws(
    () => insertGap(["one", "two", "three"], 1, 3),
    /no word is lost/,
  );
});

test("existing overlapping source cues can be retimed without a neighbour pinning their edge", () => {
  const cues = [
    { id: "a", start: 10.04, end: 12.8 },
    { id: "b", start: 10.041, end: 12.031 },
  ];
  assert.equal(moveTiming(cues, 0, "end", 12.5)[0].end, 12.5);
  assert.equal(moveTiming(cues, 1, "start", 11)[1].start, 11);
  assert.equal(moveTiming(cues, 1, "start", 9)[1].start, 9);
});

test("timing drags retain nonnegative starts and positive duration", () => {
  const cues = [{ id: "a", start: 1, end: 2 }];
  assert.equal(moveTiming(cues, 0, "start", -2)[0].start, 0);
  assert.equal(moveTiming(cues, 0, "start", 3)[0].start, 1.8);
  assert.equal(moveTiming(cues, 0, "end", 0)[0].end, 1.2);
  assert.deepEqual(moveTiming(cues, 0, "move", -2)[0], {
    id: "a",
    start: 0,
    end: 1,
  });
});

test("all active subtitles display through overlaps and nested cue endings", () => {
  const cues = [
    { id: "song", start: 0, end: 10 },
    { id: "speech", start: 2, end: 3 },
  ];
  assert.deepEqual(
    activeCues(cues, 2.5).map((cue) => cue.id),
    ["song", "speech"],
  );
  assert.deepEqual(
    activeCues(cues, 3).map((cue) => cue.id),
    ["song"],
  );
  assert.deepEqual(activeCues(cues, 10), []);
});
