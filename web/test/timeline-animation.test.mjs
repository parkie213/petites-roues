import { onTestFinished, test } from "vitest";
import assert from "node:assert/strict";
import { animateTimeline } from "../src/player/timeline-animation.js";

test("timeline follows every media frame without React clock updates and honours pauses, seeks and holds", () => {
  const previousRequest = globalThis.requestAnimationFrame;
  const previousCancel = globalThis.cancelAnimationFrame;
  let nextFrame;
  let cancelled;
  let id = 0;
  globalThis.requestAnimationFrame = (callback) => {
    nextFrame = callback;
    return ++id;
  };
  globalThis.cancelAnimationFrame = (frame) => {
    cancelled = frame;
  };
  onTestFinished(() => {
    globalThis.requestAnimationFrame = previousRequest;
    globalThis.cancelAnimationFrame = previousCancel;
  });
  const video = { current: { currentTime: 0, paused: false } };
  const held = { current: -1 };
  const drawn = [];
  const stop = animateTimeline(video, held, (time) => drawn.push(time));
  for (let frame = 1; frame <= 60; frame++) {
    video.current.currentTime = frame / 60;
    nextFrame();
  }
  assert.equal(drawn.length, 61);
  assert.equal(drawn.at(-1), 1);
  video.current.paused = true;
  nextFrame();
  nextFrame();
  assert.equal(drawn.length, 61, "paused frames do not repaint");
  video.current.currentTime = 30;
  nextFrame();
  assert.equal(drawn.at(-1), 30, "paused seeking updates immediately");
  held.current = 29.999;
  nextFrame();
  assert.equal(
    drawn.at(-1),
    29.999,
    "cue-boundary hold wins over media rounding",
  );
  video.current.paused = false;
  video.current.currentTime = 30.016;
  nextFrame();
  assert.equal(drawn.at(-1), 30.016, "resuming follows the actual media clock");
  stop();
  assert.equal(cancelled, id);
});
