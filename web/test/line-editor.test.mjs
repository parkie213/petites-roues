import { test } from "vitest";
import assert from "node:assert/strict";
import {
  changed,
  dirty,
  lineEditor,
  openLine,
} from "../src/player/lineEditor.ts";

const cues = [
  { id: "a", start: 1, end: 3, source: "Bonjour." },
  { id: "b", start: 3, end: 6, source: "Ça va ?", translation: "All right?" },
  { id: "c", start: 8, end: 10, source: "Au revoir." },
];

test("opening a line starts from it as saved, linked to the neighbours it touches", () => {
  const state = openLine(cues[1], cues);
  assert.equal(state.mode, "edit");
  assert.equal(state.editing, cues[1]);
  assert.deepEqual(state.times, { start: 3, end: 6 });
  assert.deepEqual(state.link, { previous: true, next: false });
  assert.equal(state.picked, "end");
  assert.ok(!dirty(state));
});

test("loading another line starts afresh, but keeps the picked end", () => {
  let state = openLine(cues[1], cues);
  for (const action of [
    { type: "draft", changes: { source: "Ça va !" } },
    { type: "comment", text: "Check" },
    { type: "error", message: "Oops" },
    { type: "deleting", asking: true },
    { type: "timing-status", status: "Saving timing…" },
    { type: "pick", boundary: "start" },
  ])
    state = lineEditor(state, action);
  state = lineEditor(state, { type: "load", line: cues[2], cues });
  assert.deepEqual(state, { ...openLine(cues[2], cues), picked: "start" });
});

test("adding keeps the line the Edit tab returns to", () => {
  const blank = { start: 6.5, end: 8, source: "" };
  const state = lineEditor(openLine(cues[1], cues), {
    type: "load",
    line: blank,
    cues,
    as: "add",
  });
  assert.equal(state.mode, "add");
  assert.equal(state.original, blank);
  assert.equal(state.editing, cues[1]);
  // A new line is worth keeping once it has text.
  assert.ok(!dirty(state));
  assert.ok(
    dirty(lineEditor(state, { type: "draft", changes: { source: "Oui." } })),
  );
});

test("changes to text or times are changes; a comment only makes it unsaved", () => {
  const state = openLine(cues[1], cues);
  const retimed = lineEditor(state, {
    type: "retime",
    times: { start: 3, end: 6.5 },
    link: { previous: true, next: false },
  });
  assert.ok(changed(retimed) && dirty(retimed));
  const commented = lineEditor(state, { type: "comment", text: " Check " });
  assert.ok(!changed(commented) && dirty(commented));
  assert.ok(!dirty(lineEditor(state, { type: "comment", text: "  " })));
});

test("a timing that saved itself becomes the line as saved", () => {
  const moved = { ...cues[1], start: 3, end: 8 };
  const after = [cues[0], moved, cues[2]];
  const state = lineEditor(
    lineEditor(openLine(cues[1], cues), {
      type: "retime",
      times: { start: 3, end: 8 },
      link: { previous: true, next: false },
    }),
    { type: "timing-saved", line: moved, cues: after },
  );
  assert.equal(state.original, moved);
  assert.equal(state.editing, moved);
  assert.deepEqual(state.link, { previous: true, next: true });
  assert.equal(state.timingStatus, "Timing saved");
  assert.ok(!changed(state));
});
