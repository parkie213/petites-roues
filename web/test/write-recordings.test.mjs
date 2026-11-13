import { describe, expect, it } from "vitest";
import {
  compactWrites,
  matchesWrite,
  recordWrite,
  replayWrite,
} from "../e2e/write-recordings.mjs";

const comic = "PUT /api/titles/example/progress";
const playback = "PUT /api/titles/example/progress";
const answer = (request, json) => ({
  request,
  status: 200,
  type: "application/json",
  json,
});

describe("write recordings", () => {
  it("preserves different page saves instead of replaying the last page for every PUT", () => {
    const cover = recordWrite(answer(comic, { pageId: "cover" }), {
      pageId: "cover",
    });
    const page = recordWrite(answer(comic, { pageId: "page-1" }), {
      pageId: "page-1",
    });
    const entries = compactWrites([cover, page, cover]);
    expect(entries).toEqual([page, cover]);
    expect(matchesWrite(cover, { pageId: "page-1" })).toBe(false);
    expect(matchesWrite(page, { pageId: "page-1" })).toBe(true);
    expect(matchesWrite(page, { pageId: "page-2" })).toBe(false);
  });

  it("rejects playback servers that acknowledge a different position", () => {
    expect(() =>
      recordWrite(answer(playback, { positionSeconds: 0 }), {
        positionSeconds: 6.92,
      }),
    ).toThrow("Playback save must return the submitted position");
  });

  it("records a stable checked playback contract and replays the submitted position", () => {
    const first = recordWrite(answer(playback, { positionSeconds: 6.921 }), {
      positionSeconds: 6.921,
    });
    const later = recordWrite(answer(playback, { positionSeconds: 7.018 }), {
      positionSeconds: 7.018,
    });
    expect(first).toEqual(later);
    expect(matchesWrite(first, { positionSeconds: 2.5 })).toBe(true);
    expect(replayWrite(first, { positionSeconds: 2.5 }).json).toEqual({
      positionSeconds: 2.5,
    });
    expect(matchesWrite(first, { positionSeconds: "invalid" })).toBe(false);
    expect(
      matchesWrite(first, { positionSeconds: 2.5, wrongField: true }),
    ).toBe(false);
  });

  it("preserves exact edit payloads, including joined timing boundaries", () => {
    const edits = [
      { id: "first", end: 7.02 },
      { id: "second", start: 7.02 },
    ];
    const entry = recordWrite(
      answer("POST /api/titles/example/timings", { ok: true }),
      edits,
    );
    expect(matchesWrite(entry, edits)).toBe(true);
    expect(matchesWrite(entry, [edits[0]])).toBe(false);
    expect(matchesWrite(entry, [{ ...edits[0], end: 6.92 }, edits[1]])).toBe(
      false,
    );
  });
});

it("command recordings retain the exact operation and expected state while templating only the retry nonce", () => {
  const body = {
    requestId: "first-request",
    expectedVersion: "state-1",
    commands: [{ type: "delete-video-cue", cueId: "cue-1" }],
  };
  const entry = recordWrite(
    answer("POST /api/titles/example/commands", { id: "example" }),
    body,
  );
  expect(matchesWrite(entry, { ...body, requestId: "another-request" })).toBe(
    true,
  );
  expect(matchesWrite(entry, { ...body, expectedVersion: "state-2" })).toBe(
    false,
  );
  expect(
    matchesWrite(entry, {
      ...body,
      commands: [{ type: "delete-video-cue", cueId: "cue-2" }],
    }),
  ).toBe(false);
  expect(matchesWrite(entry, { ...body, requestId: "" })).toBe(false);
});
