import test from "node:test";
import assert from "node:assert/strict";
import {
  createProgressWriter,
  initialPlaybackPosition,
  resumePosition,
  readProgressCheckpoint,
  writeProgressCheckpoint,
} from "../src/player/progress.js";

test("progress writes serialize and coalesce rapid seeks into the newest position", async () => {
  const writes = [];
  let release;
  const writer = createProgressWriter(async (position) => {
    writes.push(position);
    if (writes.length === 1)
      await new Promise((resolve) => {
        release = resolve;
      });
  }, assert.fail);
  const done = writer.save(10);
  await Promise.resolve();
  writer.save(30);
  writer.save(5);
  assert.deepEqual(writes, [10]);
  release();
  await done;
  assert.deepEqual(writes, [10, 5]);
  await writer.save(5);
  assert.deepEqual(writes, [10, 5]);
});

test("failed progress writes can retry and do not become the saved baseline", async () => {
  let attempts = 0,
    errors = 0;
  const writer = createProgressWriter(
    async () => {
      if (++attempts === 1) throw Error("offline");
    },
    () => errors++,
  );
  writer.remember(0);
  await writer.save(0);
  assert.equal(attempts, 0);
  await writer.save(12.3456);
  await writer.save(12.3456);
  await writer.save(12.346);
  assert.equal(attempts, 2);
  assert.equal(errors, 1);
  await writer.save(NaN);
  await writer.save(-1);
  await writer.save(Infinity);
  assert.equal(attempts, 2);
});

test("valid timestamp links including zero override saved progress; invalid links fall back", () => {
  assert.equal(initialPlaybackPosition("?t=12.345", 90), 12.345);
  assert.equal(initialPlaybackPosition("?t=0", 90), 0);
  for (const search of ["", "?t=", "?t=%20", "?t=no", "?t=-1", "?t=Infinity"])
    assert.equal(initialPlaybackPosition(search, 90), 90);
});

test("reload resumes a newer unsaved checkpoint instead of stale server progress", () => {
  const saved = { positionSeconds: 1038, updatedAt: "2026-10-20T10:00:00Z" };
  const checkpoint = {
    positionSeconds: 1063,
    pending: true,
    updatedAt: "2026-10-20T10:00:02Z",
  };
  assert.equal(resumePosition(saved, checkpoint), 1063);
  assert.equal(
    initialPlaybackPosition("?t=0", resumePosition(saved, checkpoint)),
    0,
  );
  assert.equal(resumePosition(saved, { ...checkpoint, pending: false }), 1038);
  assert.equal(
    resumePosition({ ...saved, updatedAt: "2026-10-20T10:00:03Z" }, checkpoint),
    1038,
  );
  assert.equal(resumePosition(saved, null), 1038);
  assert.equal(
    resumePosition(saved, { ...checkpoint, positionSeconds: -1 }),
    1038,
  );
  assert.equal(
    resumePosition(saved, { ...checkpoint, updatedAt: "invalid" }),
    1038,
  );
});

test("tab checkpoints survive reload and disabled or corrupt storage is harmless", () => {
  let stored = null;
  const storage = {
    getItem: () => stored,
    setItem: (_, value) => (stored = value),
  };
  const value = {
    positionSeconds: 1063,
    pending: true,
    updatedAt: "2026-10-20T10:00:02Z",
  };
  writeProgressCheckpoint(storage, "title", value);
  assert.deepEqual(readProgressCheckpoint(storage, "title"), value);
  stored = "corrupt";
  assert.equal(readProgressCheckpoint(storage, "title"), null);
  assert.doesNotThrow(() => writeProgressCheckpoint(undefined, "title", value));
  assert.equal(readProgressCheckpoint(undefined, "title"), null);
});
