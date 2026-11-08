import test from "node:test";
import assert from "node:assert/strict";
import { filterShelves } from "../src/library/poster-filter.js";

const ready = { label: "Tome 1", entry: { unavailable: {} } };
const missing = {
  label: "Tome 2",
  entry: { unavailable: { document: "Missing study document" } },
};
const shelves = [
  {
    key: "comics",
    groups: [{ name: "Astérix", type: "comic", slots: [ready, missing] }],
  },
  { key: "films", groups: [{ name: "Robots", type: "video", slots: [ready] }] },
];
test("search, type and attention filters combine without losing series volumes", () => {
  const result = filterShelves(shelves, {
    search: " ASTÉRIX ",
    mediaFilter: "comic",
    attentionOnly: true,
  });
  assert.equal(result.length, 1);
  assert.equal(result[0].groups[0].slots.length, 2);
  assert.equal(shelves.length, 2);
  assert.equal(
    filterShelves(shelves, { mediaFilter: "video", attentionOnly: true })
      .length,
    0,
  );
});
test("search finds volume labels and clearing filters restores every shelf", () => {
  assert.equal(filterShelves(shelves, { search: "tome 2" }).length, 1);
  assert.equal(filterShelves(shelves).length, 2);
  assert.equal(filterShelves(shelves, { search: "missing title" }).length, 0);
});
