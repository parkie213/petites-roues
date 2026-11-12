// @vitest-environment jsdom
import { expect, test, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useLibraryJob } from "../src/library/useLibraryJob.js";
import { followJob, latestJob } from "../src/library/library-job.js";

vi.mock("../src/library/library-job.js", () => ({
  followJob: vi.fn(),
  latestJob: vi.fn(),
  startJob: vi.fn(),
}));

test("reloading resumes a running job and unmounting stops polling", async () => {
  const running = { id: "running", state: "running" };
  latestJob.mockResolvedValue(running);
  const stop = vi.fn();
  followJob.mockReturnValue(stop);
  const finished = vi.fn();
  const { result, unmount } = renderHook(() => useLibraryJob(finished));
  await waitFor(() => expect(result.current.job).toEqual(running));
  const receive = followJob.mock.calls.at(-1)[1];
  const terminal = { ...running, state: "succeeded" };
  act(() => receive(terminal));
  expect(result.current.job).toBeNull();
  expect(finished).toHaveBeenCalledExactlyOnceWith(terminal, null);
  unmount();
  expect(stop).toHaveBeenCalledOnce();
});

test("reloading after completion does not repeat the completion message", async () => {
  latestJob.mockResolvedValue({ id: "finished", state: "succeeded" });
  const finished = vi.fn();
  const { result } = renderHook(() => useLibraryJob(finished));
  await act(async () => {});
  expect(result.current.job).toBeNull();
  expect(finished).not.toHaveBeenCalled();
});
