import { test } from "vitest";
import assert from "node:assert/strict";
import { preventsDefault, watchKeyAction } from "../src/player/watchKeys.ts";

/**
 * An element on the page, as `closest` sees it: inside each of the selectors in [inside], so a
 * selector list matches when any of its parts is one of them.
 */
const element = (tagName = "DIV", inside = []) => ({
  tagName,
  closest: (selector) =>
    selector.split(",").some((part) => inside.includes(part.trim()))
      ? {}
      : null,
});

/** A key press on the page body, with any of [extra] (such as shiftKey or repeat). */
const press = (key, extra = {}) => ({
  key,
  code: key === " " ? "Space" : `Key${key.toUpperCase()}`,
  repeat: false,
  shiftKey: false,
  ctrlKey: false,
  altKey: false,
  metaKey: false,
  defaultPrevented: false,
  isComposing: false,
  keyCode: 0,
  target: element(),
  ...extra,
});

test("each letter does its job on a video with lines", () => {
  const actions = Object.fromEntries(
    ["r", "e", "s", "a", "f", "t", "w"].map((key) => [
      key,
      watchKeyAction(press(key), true),
    ]),
  );
  assert.deepEqual(actions, {
    r: { type: "replay" },
    e: { type: "edit" },
    s: { type: "toggle-subtitles" },
    a: { type: "toggle-auto-pause" },
    f: { type: "fullscreen" },
    t: { type: "help", help: "translation" },
    w: { type: "help", help: "notes" },
  });
  assert.deepEqual(watchKeyAction(press("R"), true), { type: "replay" });
});

test("Space plays or pauses, even held down", () => {
  assert.deepEqual(watchKeyAction(press(" "), true), { type: "toggle-play" });
  assert.deepEqual(watchKeyAction(press(" ", { repeat: true }), false), {
    type: "toggle-play",
  });
});

test("the arrows step by line, by 2 s with Shift, and by 5 s without lines", () => {
  assert.deepEqual(watchKeyAction(press("ArrowLeft"), true), {
    type: "seek-line",
    direction: -1,
  });
  assert.deepEqual(watchKeyAction(press("ArrowRight"), true), {
    type: "seek-line",
    direction: 1,
  });
  assert.deepEqual(
    watchKeyAction(press("ArrowLeft", { shiftKey: true }), true),
    { type: "seek-by", seconds: -2 },
  );
  assert.deepEqual(watchKeyAction(press("ArrowRight"), false), {
    type: "seek-by",
    seconds: 5,
  });
});

test("a video without lines keeps help, Space, the arrows and fullscreen only", () => {
  for (const key of ["r", "e", "s", "a", "x"])
    assert.equal(watchKeyAction(press(key), false), null, key);
  assert.deepEqual(watchKeyAction(press("f"), false), { type: "fullscreen" });
  assert.deepEqual(watchKeyAction(press("t"), false), {
    type: "help",
    help: "translation",
  });
});

test("held letters, modifiers, input methods and handled keys are left alone", () => {
  assert.equal(watchKeyAction(press("r", { repeat: true }), true), null);
  assert.equal(watchKeyAction(press("t", { repeat: true }), true), null);
  for (const modifier of ["ctrlKey", "altKey", "metaKey"]) {
    assert.equal(watchKeyAction(press("s", { [modifier]: true }), true), null);
    assert.equal(watchKeyAction(press(" ", { [modifier]: true }), true), null);
  }
  assert.equal(watchKeyAction(press("e", { isComposing: true }), true), null);
  assert.equal(watchKeyAction(press(" ", { keyCode: 229 }), true), null);
  assert.equal(
    watchKeyAction(press(" ", { defaultPrevented: true }), true),
    null,
  );
});

test("keys typed into a field or pressed on an ordinary button are theirs", () => {
  assert.equal(
    watchKeyAction(press("s", { target: element("INPUT") }), true),
    null,
  );
  assert.equal(
    watchKeyAction(press(" ", { target: element("BUTTON", ["button"]) }), true),
    null,
  );
  // The toolbar's own buttons keep Space for playback after a click.
  assert.deepEqual(
    watchKeyAction(
      press(" ", {
        target: element("BUTTON", ["button", ".playback-toolbar button"]),
      }),
      true,
    ),
    { type: "toggle-play" },
  );
});

test("only what replaces a key's own effect stops the browser's", () => {
  const prevented = [
    { type: "help", help: "notes" },
    { type: "toggle-play" },
    { type: "seek-by", seconds: 2 },
    { type: "seek-line", direction: 1 },
    { type: "edit" },
  ];
  const kept = [
    { type: "replay" },
    { type: "toggle-subtitles" },
    { type: "toggle-auto-pause" },
    { type: "fullscreen" },
  ];
  for (const action of prevented)
    assert.ok(preventsDefault(action), action.type);
  for (const action of kept) assert.ok(!preventsDefault(action), action.type);
});
