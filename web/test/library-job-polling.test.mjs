import { afterEach, expect, test, vi } from "vitest";
import { followJob } from "../src/library/library-job.js";
const running = { id: "job", kind: "add", state: "running" };
const succeeded = { ...running, state: "succeeded" };
afterEach(() => vi.useRealTimers());

test("polls sequentially and stops at the terminal state", async () => {
  vi.useFakeTimers();
  let resolve;
  const fetch = vi
    .fn()
    .mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    )
    .mockResolvedValueOnce(Response.json(succeeded));
  vi.stubGlobal("fetch", fetch);
  const received = vi.fn();
  const stop = followJob(running, received);
  await vi.advanceTimersByTimeAsync(500);
  await vi.advanceTimersByTimeAsync(2000);
  expect(fetch).toHaveBeenCalledTimes(1);
  resolve(Response.json(running));
  await vi.advanceTimersByTimeAsync(0);
  await vi.advanceTimersByTimeAsync(500);
  expect(received.mock.calls.map(([job]) => job.state)).toEqual([
    "running",
    "succeeded",
  ]);
  await vi.advanceTimersByTimeAsync(2000);
  expect(fetch).toHaveBeenCalledTimes(2);
  stop();
});

test("disconnect aborts the request and suppresses late delivery", async () => {
  vi.useFakeTimers();
  let resolve;
  const fetch = vi.fn(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  vi.stubGlobal("fetch", fetch);
  const received = vi.fn();
  const stop = followJob(running, received);
  await vi.advanceTimersByTimeAsync(500);
  stop();
  expect(fetch.mock.calls[0][1].signal.aborted).toBe(true);
  resolve(Response.json(succeeded));
  await vi.advanceTimersByTimeAsync(1000);
  expect(received).not.toHaveBeenCalled();
  expect(fetch).toHaveBeenCalledTimes(1);
});

test("transient failures retry without declaring the server job failed", async () => {
  vi.useFakeTimers();
  const fetch = vi
    .fn()
    .mockRejectedValueOnce(new TypeError("network"))
    .mockResolvedValueOnce(Response.json(succeeded));
  vi.stubGlobal("fetch", fetch);
  const received = vi.fn();
  followJob(running, received);
  await vi.advanceTimersByTimeAsync(1000);
  expect(received).toHaveBeenCalledExactlyOnceWith(succeeded);
});

test("already finished jobs need no polling", () => {
  const fetch = vi.fn();
  vi.stubGlobal("fetch", fetch);
  const received = vi.fn();
  followJob(succeeded, received)();
  expect(received).toHaveBeenCalledExactlyOnceWith(succeeded);
  expect(fetch).not.toHaveBeenCalled();
});
