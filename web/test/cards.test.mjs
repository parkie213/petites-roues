import test from "node:test";
import assert from "node:assert/strict";
import { openHref, warnings } from "../src/library/cards.js";

const entry = (fields) => ({
  id: "t",
  type: "video",
  documentAvailable: true,
  videoAvailable: true,
  ...fields,
});

test("titles open the reader, watch view or notes; missing documents do not open", () => {
  assert.equal(openHref(entry({ id: "c", type: "comic" })), "/reader/c/");
  assert.equal(openHref(entry({ id: "w" })), "/watch/w/");
  assert.equal(
    openHref(entry({ id: "v", videoAvailable: false })),
    "/player/v/",
  );
  assert.equal(openHref(entry({ documentAvailable: false })), null);
});

test("media without a document: a video still plays, a comic needs its document", () => {
  const bare = { documentAvailable: false, fingerprint: "v1:abc" };
  assert.equal(openHref(entry({ ...bare })), "/video/v1%3Aabc/");
  assert.equal(openHref(entry({ ...bare, type: "comic" })), null);
  assert.deepEqual(
    warnings(entry({ unavailable: { "media-0": "No study document" } })),
    ["No study document"],
  );
});
