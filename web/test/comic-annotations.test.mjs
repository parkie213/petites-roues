import { test } from "node:test";
import assert from "node:assert/strict";
import {
  clampRegion,
  pageCues,
  pageUnits,
  pageLayout,
  regionFromDrag,
} from "../src/comic/annotations.js";

test("cue reading order is explicit and does not mutate the source document", () => {
  const document = {
    cues: [
      { id: "late", pageId: "p", order: 9 },
      { id: "other", pageId: "q", order: 1 },
      { id: "early", pageId: "p", order: 1 },
    ],
  };
  assert.deepEqual(
    pageCues(document, "p").map((c) => c.id),
    ["early", "late"],
  );
  assert.equal(document.cues[0].id, "late");
  assert.deepEqual(pageCues({}, "p"), []);
});
test("image and normalized regions share the same fitted dimensions at every zoom", () => {
  assert.deepEqual(pageLayout(1000, 600, 750, 1000, "page", 100), {
    width: 450,
    height: 600,
  });
  assert.deepEqual(pageLayout(300, 1000, 750, 1000, "page", 100), {
    width: 300,
    height: 400,
  });
  assert.deepEqual(pageLayout(600, 400, 750, 1000, "width", 100), {
    width: 600,
    height: 800,
  });
  const scaled = pageLayout(600, 400, 750, 1000, "custom", 150);
  assert.ok(Math.abs(scaled.width - 900) < 1e-6);
  assert.ok(Math.abs(scaled.height - 1200) < 1e-6);
  assert.deepEqual(pageLayout(0, 400, 750, 1000, "page", 100), {
    width: 0,
    height: 0,
  });
});
test("drawn regions clamp into valid normalized bounds with a minimum size", () => {
  assert.deepEqual(clampRegion({ x: -0.2, y: 0.5, width: 2, height: -1 }), {
    x: 0,
    y: 0.5,
    width: 1,
    height: 0.005,
  });
  assert.deepEqual(clampRegion({ x: 0.9, y: 0.9, width: 0.3, height: 0.3 }), {
    x: 0.9,
    y: 0.9,
    width: 0.1,
    height: 0.1,
  });
});
test("drag rectangles are orientation-independent", () => {
  const down = regionFromDrag({ x: 0.5, y: 0.5 }, { x: 0.2, y: 0.7 });
  const up = regionFromDrag({ x: 0.2, y: 0.7 }, { x: 0.5, y: 0.5 });
  assert.deepEqual(down, up);
  assert.deepEqual(down, { x: 0.2, y: 0.5, width: 0.3, height: 0.2 });
});

test("a text block's sentences form one unit with the block's box", () => {
  const box = [{ x: 0.1, y: 0.6, width: 0.8, height: 0.3 }];
  const cues = [
    { id: "title", pageId: "p", order: 1, regions: [] },
    { id: "s1", pageId: "p", order: 2, regions: box, block: "intro" },
    { id: "s2", pageId: "p", order: 3, regions: box, block: "intro" },
    { id: "s3", pageId: "q", order: 1, regions: box, block: "intro" },
  ];
  const units = pageUnits(cues);
  assert.deepEqual(
    units.map((u) => [u.key, u.cues.map((c) => c.id)]),
    [
      ["title", ["title"]],
      ["block:p:intro", ["s1", "s2"]],
      ["block:q:intro", ["s3"]],
    ],
  );
  assert.equal(units[1].regions, box);
});
