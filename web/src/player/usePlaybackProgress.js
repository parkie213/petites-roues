import { useEffect, useRef } from "react";
import { request } from "../shared/api.js";
import {
  createProgressWriter,
  initialPlaybackPosition,
  resumePosition,
  readProgressCheckpoint,
  writeProgressCheckpoint,
} from "./progress.js";

/** Saves changed progress every five seconds while playing and at user stopping points. */
export function usePlaybackProgress({
  api,
  enabled,
  videoRef,
  position,
  restore,
  onError,
}) {
  const latest = useRef({ position, restore, onError });
  latest.current = { position, restore, onError };
  useEffect(() => {
    const video = videoRef.current;
    if (!enabled || !video) return;
    let ready = false;
    let disposed = false;
    let interacted = false;
    let restoring = false;
    const abort = new AbortController();
    const checkpointKey = "sacrebleu:resume:" + api;
    let storage;
    try {
      storage = window.sessionStorage;
    } catch {
      /* Storage is optional. */
    }
    const local = readProgressCheckpoint(storage, checkpointKey);
    let lastCheckpoint = null;
    const checkpoint = () => {
      if (!ready || video.readyState < 1) return;
      const value = latest.current.position(video);
      if (!Number.isFinite(value) || value < 0) return;
      const positionSeconds = Math.round(value * 1000) / 1000;
      if (positionSeconds === lastCheckpoint) return;
      lastCheckpoint = positionSeconds;
      writeProgressCheckpoint(storage, checkpointKey, {
        positionSeconds,
        updatedAt: new Date().toISOString(),
        pending: true,
      });
    };
    const writer = createProgressWriter(
      async (positionSeconds) => {
        const saved = await request(api + "/progress", {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ positionSeconds }),
          keepalive: true,
        });
        const current = readProgressCheckpoint(storage, checkpointKey);
        if (current?.positionSeconds === positionSeconds)
          writeProgressCheckpoint(storage, checkpointKey, {
            ...saved,
            pending: false,
          });
      },
      () =>
        latest.current.onError(
          "Playback position could not be saved. It will retry when playback changes.",
        ),
    );
    const save = () => {
      if (ready && video.readyState >= 1) {
        checkpoint();
        writer.save(latest.current.position(video));
      }
    };
    const interaction = () => {
      interacted = true;
    };
    const progress = request(api + "/progress", { signal: abort.signal }).catch(
      (error) => {
        if (error.name !== "AbortError" && !disposed)
          latest.current.onError(
            "Saved playback position could not be loaded.",
          );
        return { positionSeconds: 0 };
      },
    );
    const initialize = async () => {
      if (ready || restoring) return;
      restoring = true;
      const saved = await progress;
      if (disposed) return;
      if (!interacted) {
        const start = initialPlaybackPosition(
          location.search,
          resumePosition(saved, local),
        );
        writer.remember(saved.positionSeconds);
        latest.current.restore(start);
      }
      ready = true;
      restoring = false;
      if (interacted) save();
    };
    const visibility = () => {
      if (document.visibilityState === "hidden") save();
    };
    const timer = setInterval(() => {
      if (!video.paused && !video.seeking) save();
    }, 5000);
    video.addEventListener("loadedmetadata", initialize);
    video.addEventListener("play", interaction);
    video.addEventListener("seeking", interaction);
    video.addEventListener("timeupdate", checkpoint);
    video.addEventListener("pause", save);
    video.addEventListener("seeked", save);
    video.addEventListener("ended", save);
    window.addEventListener("pagehide", save);
    document.addEventListener("visibilitychange", visibility);
    if (video.readyState >= 1) initialize();
    return () => {
      save();
      disposed = true;
      abort.abort();
      clearInterval(timer);
      video.removeEventListener("loadedmetadata", initialize);
      video.removeEventListener("play", interaction);
      video.removeEventListener("seeking", interaction);
      video.removeEventListener("timeupdate", checkpoint);
      video.removeEventListener("pause", save);
      video.removeEventListener("seeked", save);
      video.removeEventListener("ended", save);
      window.removeEventListener("pagehide", save);
      document.removeEventListener("visibilitychange", visibility);
    };
  }, [api, enabled, videoRef]);
}
