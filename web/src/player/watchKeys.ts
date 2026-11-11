import { useEffect, useRef } from "react";
import { isComposing } from "./languages.js";
import { playbackSpaceTarget, videoHelpShortcut } from "./playback.js";

/** Which help a key shows over the video: the whole translation, or the line's notes. */
export type VideoHelp = "translation" | "notes";

/** What a key does on the viewing page. */
export type WatchAction =
  | { type: "help"; help: VideoHelp }
  | { type: "toggle-play" }
  | { type: "seek-by"; seconds: number }
  | { type: "seek-line"; direction: -1 | 1 }
  | { type: "replay" }
  | { type: "edit" }
  | { type: "toggle-subtitles" }
  | { type: "toggle-auto-pause" }
  | { type: "fullscreen" };

/** The parts of a key press the shortcuts read. */
export type WatchKey = Pick<
  KeyboardEvent,
  | "key"
  | "code"
  | "repeat"
  | "shiftKey"
  | "ctrlKey"
  | "altKey"
  | "metaKey"
  | "defaultPrevented"
  | "isComposing"
  | "keyCode"
  | "target"
>;

/**
 * What [event] does on the viewing page, or null. T and W show help; Space plays or pauses; the
 * arrows step by line (with Shift, by 2 s); R replays the line, E edits it, S shows or hides the
 * subtitles, A toggles auto-pause and F goes fullscreen. A video without lines ([hasCues] false)
 * keeps help, Space and F, and its arrows step by 5 s. Keys typed into a field, held with Ctrl,
 * Alt or ⌘, or composed in an input method are left alone.
 */
export function watchKeyAction(
  event: WatchKey,
  hasCues: boolean,
): WatchAction | null {
  if (event.defaultPrevented || isComposing(event)) return null;
  const help = videoHelpShortcut(event) as VideoHelp | null;
  if (help) return { type: "help", help };
  if (
    event.ctrlKey ||
    event.altKey ||
    event.metaKey ||
    !playbackSpaceTarget(event.target)
  )
    return null;
  if (event.code === "Space") return { type: "toggle-play" };
  if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
    const direction = event.key === "ArrowLeft" ? -1 : 1;
    if (!hasCues) return { type: "seek-by", seconds: 5 * direction };
    if (event.shiftKey) return { type: "seek-by", seconds: 2 * direction };
    return { type: "seek-line", direction };
  }
  if (event.repeat) return null;
  const key = event.key.toLowerCase();
  if (key === "f") return { type: "fullscreen" };
  if (!hasCues) return null;
  if (key === "r") return { type: "replay" };
  if (key === "e") return { type: "edit" };
  if (key === "s") return { type: "toggle-subtitles" };
  if (key === "a") return { type: "toggle-auto-pause" };
  return null;
}

/** Whether [action] replaces the key's own effect, such as Space scrolling or a button's press. */
export function preventsDefault(action: WatchAction) {
  return ["help", "toggle-play", "seek-by", "seek-line", "edit"].includes(
    action.type,
  );
}

/**
 * Listens for the viewing page's keys while mounted and hands each action to [run], the latest
 * one given, so it always sees the current line and time.
 */
export function useWatchShortcuts(
  hasCues: boolean,
  run: (action: WatchAction) => void,
) {
  const latest = useRef({ hasCues, run });
  latest.current = { hasCues, run };
  useEffect(() => {
    function keydown(event: KeyboardEvent) {
      const action = watchKeyAction(event, latest.current.hasCues);
      if (!action) return;
      if (preventsDefault(action)) event.preventDefault();
      latest.current.run(action);
    }
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, []);
}
