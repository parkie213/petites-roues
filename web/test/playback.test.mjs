import { test } from "vitest";
import assert from "node:assert/strict";
import {
  pauseAtCueBoundary,
  playbackSpaceTarget,
  subtitleSeekTime,
} from "../src/player/playback.js";
import { currentCue } from "../src/player/model.ts";

test("subtitle arrows restart, step between cues and stop at the ends", () => {
  const cues = [{ start: 2 }, { start: 5 }, { start: 10 }];
  assert.equal(subtitleSeekTime(cues, 1, 7, -1), 5);
  assert.equal(subtitleSeekTime(cues, 1, 5.1, -1), 2);
  assert.equal(subtitleSeekTime(cues, 1, 7, 1), 10);
  assert.equal(subtitleSeekTime(cues, 0, 2, -1), 2);
  assert.equal(subtitleSeekTime(cues, 2, 12, 1), 10);
  assert.equal(subtitleSeekTime(cues, -1, 0, 1), 2);
  assert.equal(subtitleSeekTime([], 0, 0, 1), null);
});

test("media seek rounding cannot briefly select the previous subtitle", () => {
  const cues = [
    { start: 527.82, end: 531.29 },
    { start: 531.29, end: 536.07 },
  ];
  assert.equal(currentCue(cues, 531.289999), 1);
  assert.equal(currentCue(cues, 531.289), 0);
});

test("Space continues from focused transport and transcript buttons without activating them again", () => {
  const target = (tagName, button = false, transport = false) => ({
    tagName,
    closest: (selector) =>
      selector === "button"
        ? button
        : selector ===
            ".cue-row, .playback-toolbar button, .alignment-phrase, .video-subtitle-phrase"
          ? transport
          : null,
  });
  assert.equal(playbackSpaceTarget(target("BUTTON", true, true)), true);
  assert.equal(playbackSpaceTarget(target("SPAN", true, true)), true);
  assert.equal(playbackSpaceTarget(target("BUTTON", true, false)), false);
  for (const tag of ["INPUT", "TEXTAREA", "SELECT", "A"])
    assert.equal(playbackSpaceTarget(target(tag)), false);
  assert.equal(playbackSpaceTarget({ isContentEditable: true }), false);
  assert.equal(playbackSpaceTarget(target("DIV")), true);
});

test("auto-pause holds the completed subtitle without seeking back across an adjacent boundary", () => {
  const cues = [
    { start: 1, end: 2 },
    { start: 2, end: 3 },
  ];
  const video = {
    paused: false,
    get currentTime() {
      return 2.04;
    },
    set currentTime(_) {
      assert.fail("Auto-pause must not seek the video");
    },
    pause() {
      this.paused = true;
    },
  };
  const heldTime = pauseAtCueBoundary(video, cues[0]);
  assert.equal(video.paused, true);
  assert.equal(video.currentTime, 2.04);
  assert.equal(currentCue(cues, heldTime), 0);
  assert.equal(currentCue(cues, video.currentTime), 1);
});

// Whole-sentence help should work after transport clicks, but never while typing.
test("video help shortcuts toggle English and notes without stealing text or modified keys", async () => {
  const { videoHelpShortcut } = await import("../src/player/playback.js");
  const event = (key, target = { tagName: "DIV" }, extra = {}) => ({
    key,
    target,
    ...extra,
  });
  assert.equal(videoHelpShortcut(event("t")), "translation");
  assert.equal(videoHelpShortcut(event("W")), "notes");
  const transport = {
    tagName: "BUTTON",
    closest: (selector) =>
      selector === "button" || selector.includes(".playback-toolbar")
        ? {}
        : null,
  };
  assert.equal(videoHelpShortcut(event("t", transport)), "translation");
  for (const tagName of ["INPUT", "TEXTAREA", "SELECT", "A"])
    assert.equal(videoHelpShortcut(event("t", { tagName })), null);
  assert.equal(
    videoHelpShortcut(event("w", { isContentEditable: true })),
    null,
  );
  for (const modifier of [
    "ctrlKey",
    "metaKey",
    "altKey",
    "repeat",
    "defaultPrevented",
  ])
    assert.equal(
      videoHelpShortcut(event("t", undefined, { [modifier]: true })),
      null,
    );
  assert.equal(videoHelpShortcut(event("r")), null);
});

test("Mantine slider thumbs and their children retain Space and help keys", async () => {
  const { videoHelpShortcut } = await import("../src/player/playback.js");
  for (const tagName of ["DIV", "SPAN"]) {
    const target = {
      tagName,
      closest: (selector) => (selector === '[role="slider"]' ? {} : null),
    };
    assert.equal(playbackSpaceTarget(target), false);
    assert.equal(videoHelpShortcut({ key: "t", target }), null);
    assert.equal(videoHelpShortcut({ key: "w", target }), null);
  }
});
