// @vitest-environment jsdom
import { renderHook, waitFor } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { usePlaybackProgress } from "../src/player/usePlaybackProgress.js";

/** A <video> whose readiness and position the test sets, since jsdom can't play media. */
function fakeVideo() {
  const video = document.createElement("video");
  let readyState = 0;
  let currentTime = 0;
  let paused = true;
  Object.defineProperties(video, {
    readyState: { get: () => readyState },
    currentTime: {
      get: () => currentTime,
      set: (seconds) => {
        currentTime = seconds;
      },
    },
    paused: { get: () => paused },
  });
  return {
    video,
    loaded() {
      readyState = 1;
      video.dispatchEvent(new Event("loadedmetadata"));
    },
    pauseAt(seconds) {
      currentTime = seconds;
      paused = true;
      video.dispatchEvent(new Event("pause"));
    },
  };
}

/** The progress API: answers a load with [saved] and records each save's body. */
function progressApi(saved) {
  const saves = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options = {}) => {
      if (options.method === "PUT") {
        const body = JSON.parse(options.body);
        saves.push(body.positionSeconds);
        return Response.json({ ...body, updatedAt: "2026-01-02T00:00:00Z" });
      }
      return Response.json(saved);
    }),
  );
  return saves;
}

function renderProgress(media) {
  const onReady = vi.fn();
  const onError = vi.fn();
  renderHook(() =>
    usePlaybackProgress({
      api: "/api/titles/peppa",
      enabled: true,
      videoRef: { current: media.video },
      position: (video) => video.currentTime,
      restore: (seconds) => (media.video.currentTime = seconds),
      onError,
      onReady,
    }),
  );
  return { onReady, onError };
}

test("the saved position is restored once the video's metadata loads", async () => {
  sessionStorage.clear();
  progressApi({ positionSeconds: 12.5, updatedAt: "2026-01-01T00:00:00Z" });
  const media = fakeVideo();
  const { onReady, onError } = renderProgress(media);

  media.loaded();

  await waitFor(() => expect(onReady).toHaveBeenCalledOnce());
  expect(media.video.currentTime).toBe(12.5);
  expect(fetch).toHaveBeenCalledWith(
    "/api/titles/peppa/progress",
    expect.anything(),
  );
  expect(onError).not.toHaveBeenCalled();
});

test("pausing saves the position once, and an unchanged position isn't saved again", async () => {
  sessionStorage.clear();
  const saves = progressApi({ positionSeconds: 0 });
  const media = fakeVideo();
  const { onReady } = renderProgress(media);
  media.loaded();
  await waitFor(() => expect(onReady).toHaveBeenCalled());

  media.pauseAt(20.25);
  await waitFor(() => expect(saves).toEqual([20.25]));

  media.pauseAt(20.25);
  media.pauseAt(31);
  await waitFor(() => expect(saves).toEqual([20.25, 31]));
});
