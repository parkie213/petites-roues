import { useEffect, useRef, useState } from "react";
import type { RefObject } from "react";
import type { Timed } from "./model.ts";
import { pauseAtCueBoundary } from "./playback.js";
import { usePlaybackProgress } from "./usePlaybackProgress.js";

interface Options {
  /** The title's API path, or null for a video without a study document. */
  api: string | null;
  /** The loaded document; nothing starts or is restored before it is. */
  doc: unknown;
  cues: readonly Timed[];
  /** Whether playback pauses at the end of each line. */
  autoPause: boolean;
  setStatus: (message: string) => void;
  /** Whether the position is restored on opening and saved as it changes. */
  saveProgress?: boolean;
  /** Whether playback starts by itself once the video is ready. */
  autoPlay?: boolean;
}

/**
 * Owns the media clock, line-end pauses, replay and saved playback position for one `<video>`,
 * which the caller renders with {@link video} as its ref and {@link videoEvents} as its handlers.
 * While paused at a line end, the clock is held just inside that line ({@link heldTime}) rather
 * than seeking back, so the next line's subtitle never flashes.
 */
export function usePlayerPlayback({
  api,
  doc,
  cues,
  autoPause,
  setStatus,
  saveProgress = true,
  autoPlay = false,
}: Options) {
  const video = useRef<HTMLVideoElement>(null);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const lastTime = useRef(0);
  // The line playback is paused at the end of, and the time held there; -1 when not held.
  const heldCue = useRef(-1);
  const heldTime = useRef(-1);
  const replayEnd = useRef<number | null>(null);
  const replayToken = useRef(0);
  const state = useRef({ cues, autoPause });
  state.current = { cues, autoPause };
  useEffect(() => {
    let frame: number,
      last = 0;
    function tick(now: number) {
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
  /** Forgets a line-end hold, so playback and the clock move on freely. */
  function release() {
    heldCue.current = -1;
    heldTime.current = -1;
  }
  function seek(value: number) {
    const element = video.current;
    if (!element) return;
    release();
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
    position: (element: HTMLVideoElement) =>
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
    // Starts once per document; `play` is a new function each render and must not restart it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoPlay, saveProgress, doc]);
  // Browsers refuse to start a page's sound until the person clicks on it, but allow it muted.
  const [mutedForAutoplay, setMutedForAutoplay] = useState(false);
  function play() {
    const element = video.current;
    element?.play().catch((e: DOMException) => {
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
  /** Plays a paused video and pauses a playing one. */
  function toggle() {
    if (video.current?.paused) play();
    else pause();
  }
  /** Plays line [index] from its start and pauses at its end. */
  async function replay(index: number) {
    const element = video.current;
    const line = cues[index];
    if (!line || !element) return;
    const token = ++replayToken.current;
    pause();
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
    release();
    lastTime.current = line.start;
    replayEnd.current = line.end;
    play();
  }
  /** The `<video>`'s handlers that keep the clock and the playing state in step with it. */
  const videoEvents = {
    onPlay() {
      heldTime.current = -1;
      setPlaying(true);
    },
    onPause() {
      setPlaying(false);
    },
    onSeeking() {
      const value = video.current?.currentTime ?? 0;
      // A seek elsewhere (not the hold's own position) ends the hold.
      if (Math.abs(value - heldTime.current) > 0.0001) release();
      lastTime.current = value;
      setTime(value);
    },
  };

  return {
    video,
    videoEvents,
    time,
    playing,
    /** Where a line-end pause holds the clock, or -1; read by the timeline's playhead. */
    heldTime: heldTime as Readonly<RefObject<number>>,
    seek,
    play,
    pause,
    toggle,
    replay,
    release,
    mutedForAutoplay,
    unmute,
  };
}
