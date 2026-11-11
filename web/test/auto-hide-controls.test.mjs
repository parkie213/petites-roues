import { test } from "vitest";
import assert from "node:assert/strict";
import { revealsControls } from "../src/player/useAutoHideControls.ts";

const player = { top: 100, bottom: 600 };
const plain = { closest: () => null };
const subtitle = {
  closest: (selector) =>
    selector.includes(".video-subtitle-line") ? {} : null,
};

const pointer = (clientY, extra = {}) => ({
  target: plain,
  clientY,
  pointerType: "mouse",
  type: "pointermove",
  ...extra,
});

test("the pointer near the top or bottom edge reveals the controls", () => {
  assert.ok(revealsControls(pointer(120), player));
  assert.ok(revealsControls(pointer(580), player));
  assert.ok(!revealsControls(pointer(350), player));
});

test("a touch tap reveals them anywhere, since touch has no hover", () => {
  assert.ok(
    revealsControls(
      pointer(350, { pointerType: "touch", type: "pointerdown" }),
      player,
    ),
  );
  assert.ok(
    !revealsControls(
      pointer(350, { pointerType: "touch", type: "pointermove" }),
      player,
    ),
  );
});

test("exploring a subtitle never brings them up, even at an edge", () => {
  assert.ok(!revealsControls(pointer(580, { target: subtitle }), player));
  assert.ok(
    !revealsControls(
      pointer(350, {
        target: subtitle,
        pointerType: "touch",
        type: "pointerdown",
      }),
      player,
    ),
  );
});
