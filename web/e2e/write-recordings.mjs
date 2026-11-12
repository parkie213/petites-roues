import assert from "node:assert/strict";
import { isDeepStrictEqual } from "node:util";

const position = "<request.positionSeconds>";
const playback = (entry) =>
  /^PUT \/api\/titles\/[^/]+\/progress$/.test(entry.request);

/**
 * Keep a write's payload with its answer. Playback saves are a checked echo contract: verify the
 * real server returned the submitted position, then store a template instead of wall-clock time.
 * Reading progress and document edits retain their actual payloads and answers.
 */
export function recordWrite(entry, body) {
  if (playback(entry)) {
    assert.equal(typeof body?.positionSeconds, "number");
    assert(Number.isFinite(body.positionSeconds));
    assert.equal(
      entry.json?.positionSeconds,
      body.positionSeconds,
      "Playback save must return the submitted position",
    );
    return {
      ...entry,
      body: { ...body, positionSeconds: position },
      json: { ...entry.json, positionSeconds: position },
    };
  }
  return { ...entry, body };
}

/** Match payloads as well as URLs; a saved cover must never stand in for a different page. */
export function matchesWrite(entry, body) {
  if (playback(entry)) {
    if (
      typeof body?.positionSeconds !== "number" ||
      !Number.isFinite(body.positionSeconds)
    )
      return false;
    body = { ...body, positionSeconds: position };
  }
  return isDeepStrictEqual(entry.body, body);
}

/** Instantiate only the playback contract that was checked against the real server. */
export function replayWrite(entry, body) {
  return playback(entry)
    ? {
        ...entry,
        json: { ...entry.json, positionSeconds: body.positionSeconds },
      }
    : entry;
}

/** Keep the last identical PUT contract, without collapsing writes with different payloads. */
export function compactWrites(entries) {
  return entries.filter(
    (entry, index, all) =>
      !entry.request.startsWith("PUT ") ||
      !all
        .slice(index + 1)
        .some(
          (next) =>
            next.request === entry.request &&
            isDeepStrictEqual(next.body, entry.body),
        ),
  );
}
