import { useEffect, useRef } from "react";
import type { RefObject } from "react";

/**
 * Previews of part of a line: [preview] plays from one time to another and pauses there. Pausing,
 * by reaching the end or by hand, ends the preview; [onPreview] is told while one plays, so
 * auto-pause can let it run past the line's saved end. Unmounting stops a preview still playing.
 */
export function useLinePreview(
  video: RefObject<HTMLVideoElement | null>,
  onPreview: (previewing: boolean) => void,
) {
  const end = useRef<number | null>(null);
  useEffect(() => {
    const element = video.current;
    let frame: number;
    function tick() {
      const until = end.current;
      if (element && until !== null && element.currentTime >= until) {
        // Read before pausing: the pause ends the preview and forgets its end.
        end.current = null;
        element.pause();
        element.currentTime = Math.max(0, until - 0.001);
      }
      frame = requestAnimationFrame(tick);
    }
    frame = requestAnimationFrame(tick);
    const stopped = () => {
      end.current = null;
      onPreview(false);
    };
    element?.addEventListener("pause", stopped);
    return () => {
      cancelAnimationFrame(frame);
      element?.removeEventListener("pause", stopped);
      if (end.current !== null) element?.pause();
      onPreview(false);
    };
  }, [video, onPreview]);
  return {
    /** Plays from [from] to [to] with [seek] and [play], then pauses there. */
    preview(
      from: number,
      to: number,
      seek: (seconds: number) => void,
      play: () => void,
    ) {
      seek(Math.max(0, from));
      end.current = to;
      onPreview(true);
      play();
    },
    /** Forgets the preview playing, so its end no longer pauses. */
    cancel() {
      end.current = null;
    },
  };
}
