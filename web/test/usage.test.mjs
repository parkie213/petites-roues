import { test } from "vitest";
import assert from "node:assert/strict";
import { formatBytes, usageLine } from "../src/library/usage.js";

test("formats sizes in decimal units", () => {
  assert.equal(formatBytes(0), "0 B");
  assert.equal(formatBytes(999), "999 B");
  assert.equal(formatBytes(1500), "1.5 kB");
  assert.equal(formatBytes(41_234_000_000), "41.2 GB");
  assert.equal(formatBytes(210_400_000_000), "210 GB");
});

test("names repositories only when they sit on several disks", () => {
  const disk = (repositories, freeBytes) => ({
    repositories,
    freeBytes,
    totalBytes: 1e12,
  });
  assert.equal(
    usageLine({
      libraryBytes: 41.2e9,
      disks: [disk(["films", "manga"], 210e9)],
    }),
    "Library 41.2 GB · 210 GB free",
  );
  assert.equal(
    usageLine({
      libraryBytes: 5e9,
      disks: [disk(["films"], 210e9), disk(["manga"], 32e9)],
    }),
    "Library 5.0 GB · 210 GB free (films) · 32.0 GB free (manga)",
  );
  assert.equal(usageLine({ libraryBytes: 0, disks: [] }), "Library 0 B");
});
