import { useEffect, useRef, useState } from "react";
import { pauseAtCueBoundary } from "./playback.js";
import { usePlaybackProgress } from "./usePlaybackProgress.js";

export const playbackSpeeds = [0.5, 0.6, 0.8, 1, 1.25, 1.5];

/** Owns the media clock, cue-boundary pauses, replay and persisted playback position. */
export function usePlayerPlayback({
  api,
  doc,
  cues,
  autoPause,
  speed,
  setSelected,
  setStatus,
  saveProgress = true,
  autoPlay = false,
}) {
  const video = useRef(null);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const lastTime = useRef(0);
  const heldCue = useRef(-1);
  const heldTime = useRef(-1);
  const replayEnd = useRef(null);
  const replayToken = useRef(0);
  const state = useRef({});
  state.current = { cues, autoPause };
  useEffect(() => {
    if (video.current) {
      video.current.playbackRate = playbackSpeeds.includes(speed) ? speed : 1;
      video.current.defaultPlaybackRate = playbackSpeeds.includes(speed)
        ? speed
        : 1;
    }
  }, [speed, doc]);
  useEffect(() => {
    let frame,
      last = 0;
    function tick(now) {
      const element = video.current;
      if (element && now - last > 40) {
        last = now;
        let next = element.currentTime;
        if (!element.paused && !element.seeking) {
          const latest = state.current;
          let boundary = -1;
          if (latest.autoPause)
            boundary = latest.cues.findIndex(
              (c, i) =>
                i !== heldCue.current &&
                c.end > lastTime.current &&
                c.end <= next,
            );
          if (replayEnd.current != null && next >= replayEnd.current) {
            boundary = latest.cues.findIndex(
              (c) => c.end === replayEnd.current,
            );
            replayEnd.current = null;
          }
          if (boundary >= 0) {
            const line = latest.cues[boundary];
            heldCue.current = boundary;
            next = pauseAtCueBoundary(element, line);
            heldTime.current = next;
            replayEnd.current = null;
          }
        }
        if (element.paused && heldTime.current >= 0) next = heldTime.current;
        lastTime.current = next;
        setTime(next);
      }
      frame = requestAnimationFrame(tick);
    }
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, []);
  function seek(value) {
    const element = video.current;
    if (!element) return;
    heldCue.current = -1;
    heldTime.current = -1;
    replayEnd.current = null;
    element.currentTime = Math.max(
      0,
      Number.isFinite(element.duration)
        ? Math.min(value, element.duration)
        : value,
    );
    lastTime.current = element.currentTime;
    setTime(element.currentTime);
  }
  usePlaybackProgress({
    api,
    enabled: saveProgress && Boolean(doc),
    videoRef: video,
    position: (element) =>
      heldTime.current >= 0 ? heldTime.current : element.currentTime,
    restore: seek,
    onError: setStatus,
    onReady: () => autoPlay && play(),
  });
  // Without saved progress there is nothing to restore: start as soon as the video can.
  useEffect(() => {
    const element = video.current;
    if (!autoPlay || saveProgress || !doc || !element) return;
    const start = () => play();
    if (element.readyState >= 1) start();
    else element.addEventListener("loadedmetadata", start, { once: true });
    return () => element.removeEventListener("loadedmetadata", start);
  }, [autoPlay, saveProgress, doc]);
  // Browsers refuse to start a page's sound until the person clicks on it, but allow it muted.
  const [mutedForAutoplay, setMutedForAutoplay] = useState(false);
  function play() {
    const element = video.current;
    element?.play().catch((e) => {
      if (e.name === "NotAllowedError" && !element.muted) {
        element.muted = true;
        element.play().then(
          () => setMutedForAutoplay(true),
          () => {
            element.muted = false;
            setStatus("The browser blocked playback: press play to start.");
          },
        );
      } else if (e.name !== "AbortError") setStatus(e.message);
    });
  }
  /** Turns the sound on after a muted autoplay; a click is what the browser waits for. */
  function unmute() {
    if (video.current) video.current.muted = false;
    setMutedForAutoplay(false);
  }
  function pause() {
    video.current?.pause();
  }
  async function replay(index) {
    if (!cues[index] || !video.current) return;
    const token = ++replayToken.current,
      element = video.current;
    pause();
    setSelected(index);
    const line = cues[index];
    if (
      element.seeking ||
      Math.abs(element.currentTime - line.start) > 0.0001
    ) {
      await new Promise((resolve) => {
        element.addEventListener("seeked", resolve, { once: true });
        seek(line.start);
      });
    }
    if (token !== replayToken.current) return;
    heldCue.current = -1;
    heldTime.current = -1;
    lastTime.current = line.start;
    replayEnd.current = line.end;
    play();
  }

  return {
    video,
    time,
    setTime,
    playing,
    setPlaying,
    heldCue,
    heldTime,
    replayEnd,
    lastTime,
    seek,
    play,
    pause,
    replay,
    mutedForAutoplay,
    unmute,
  };
}
