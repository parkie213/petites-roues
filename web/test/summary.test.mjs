import { test } from "vitest";
import assert from "node:assert/strict";
import { librarySummary } from "../src/library/summary.ts";

test("the summary counts what posters show, and documents that found no media", () => {
  assert.equal(librarySummary([{ entries: [], media: [] }]), null);
  const summary = librarySummary([
    {
      entries: [
        { type: "video", mediaAvailable: true, unavailable: {} },
        {
          type: "video",
          mediaAvailable: true,
          unavailable: { audio: "No audio track this player can play (AC3)" },
        },
        {
          type: "comic",
          mediaAvailable: false,
          unavailable: { title: "CBZ not found" },
        },
      ],
      media: [
        { kind: "comic", warnings: ["No study document"] },
        { kind: "video", warnings: ["No study document"] },
      ],
    },
  ]);
  assert.deepEqual(summary, {
    counts: "3 videos · 1 comic",
    unlinked: "1 study document without media",
    attention: "3 warnings",
  });
  assert.deepEqual(
    librarySummary([
      { entries: [{ type: "video", mediaAvailable: false }], media: [] },
    ]),
    {
      counts: "",
      unlinked: "1 study document without media",
      attention: null,
    },
  );
});
