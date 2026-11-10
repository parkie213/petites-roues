import { test } from "vitest";
import assert from "node:assert/strict";
import { parseSubtitles } from "../src/player/subtitles.js";

test("reads multiline Whisper SRT with millisecond timings", () => {
  assert.deepEqual(
    parseSubtitles(
      "\uFEFF1\r\n00:00:01,250 --> 00:00:04,500\r\nBonjour\r\ntout le monde.\r\n",
    ),
    [{ start: 1.25, end: 4.5, source: "Bonjour\ntout le monde." }],
  );
  assert.throws(() => parseSubtitles("not subtitles"), /No valid subtitles/);
});
