import { test } from "vitest";
import assert from "node:assert/strict";
import {
  dragLink,
  dragTimes,
  draftLines,
  timelineHit,
} from "../src/player/timeline.ts";

const lines = [
  { id: "a", start: 1, end: 2.5, source: "Un" },
  { id: "b", start: 2.5, end: 4, source: "Deux" },
  { id: "c", start: 4.24, end: 6, source: "Trois" },
];
const linked = { previous: true, next: true };

test("the timeline shows the edited line as drafted, with the neighbours that follow it", () => {
  const draft = { ...lines[1], source: "Deux !", start: 2.7, end: 4.1 };
  assert.deepEqual(draftLines(lines, lines[1], draft, linked, false), [
    { ...lines[0], end: 2.7 },
    draft,
    { ...lines[2], start: 4.34 },
  ]);
  assert.deepEqual(
    draftLines(lines, lines[1], draft, { previous: false, next: false }, false),
    [lines[0], draft, lines[2]],
  );
  const added = { start: 3, end: 3.5, source: "" };
  assert.deepEqual(
    draftLines(lines, added, added, linked, true).map((line) => line.source),
    ["Un", "Deux", "", "Trois"],
  );
});

test("pressing the timeline grabs the edited line's ends or body, selects another line, or seeks", () => {
  // 100 pixels a second from 0 s: line b spans 250–400 px.
  const at = (x) => timelineHit(lines, x, 0, 100, 1);
  assert.deepEqual(at(253), { kind: "boundary", boundary: "start" });
  assert.deepEqual(at(396), { kind: "boundary", boundary: "end" });
  assert.deepEqual(at(300), { kind: "move", offset: 0.5 });
  assert.deepEqual(at(150), { kind: "select", index: 0, at: 1.5 });
  assert.deepEqual(at(410), { kind: "seek", at: 4.1 });
  assert.deepEqual(timelineHit(lines, 300, 0, 100, -1), {
    kind: "select",
    index: 1,
    at: 3,
  });
});

test("dragging keeps a short line, rounds to the millisecond and moves a whole line by its grip", () => {
  const times = { start: 2.5, end: 4 };
  assert.deepEqual(dragTimes(times, "end", 4.12345), {
    start: 2.5,
    end: 4.123,
  });
  assert.deepEqual(dragTimes(times, "end", 1), { start: 2.5, end: 2.7 });
  assert.deepEqual(dragTimes(times, "start", 5), { start: 3.8, end: 4 });
  assert.deepEqual(dragTimes(times, "move", 3.5, 0.5), { start: 3, end: 4.5 });
});

test("a dragged end takes the neighbour it meets along unless Alt opens a gap", () => {
  const none = { previous: false, next: false };
  assert.deepEqual(dragLink(lines, lines[1], "start", false, none), {
    previous: true,
    next: false,
  });
  assert.deepEqual(dragLink(lines, lines[1], "start", true, linked), {
    previous: false,
    next: true,
  });
  // Line c does not meet line b, so it never follows by default.
  assert.deepEqual(dragLink(lines, lines[1], "move", false, none), {
    previous: true,
    next: false,
  });
});
