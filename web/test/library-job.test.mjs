import { test } from "vitest";
import assert from "node:assert/strict";
import { describeJob, jobSucceeded } from "../src/library/library-job.js";

const job = (progress) => ({
  id: "j",
  kind: "add",
  state: "running",
  progress,
});

test("a job names its phase, its count and the file it is on", () => {
  assert.deepEqual(describeJob(job(null)), {
    label: "Starting…",
    detail: null,
    value: null,
  });
  assert.deepEqual(
    describeJob(
      job({
        folder: "fr",
        phase: "media",
        done: 119,
        total: 288,
        current: "comics/naruto/Naruto-T12.cbz",
      }),
    ),
    {
      label: "Checking 288 media files in fr · 120 of 288",
      detail: "comics/naruto/Naruto-T12.cbz",
      value: 41,
    },
  );
  assert.equal(
    describeJob(
      job({
        folder: "fr",
        phase: "documents",
        done: 0,
        total: 1,
        current: "a.json",
      }),
    ).label,
    "Reading study documents in fr · 1 of 1",
  );
  assert.deepEqual(
    describeJob(job({ folder: "fr", phase: "scanning", done: 0, total: 0 })),
    { label: "Looking through fr…", detail: null, value: null },
  );
});

test("a finished job says what it did", () => {
  assert.equal(jobSucceeded({ kind: "add" }, null), "Folder added.");
  assert.equal(jobSucceeded({ kind: "refresh" }, {}), "Library refreshed.");
  assert.equal(
    jobSucceeded({ kind: "reset" }, { kind: "reset" }),
    "Library reset and rebuilt.",
  );
  assert.equal(
    jobSucceeded({ kind: "refresh" }, { repositoryId: "r" }),
    "Folder refreshed.",
  );
});
