import { useEffect, useRef } from "react";
import type { RefObject } from "react";
import type { CueView } from "../shared/types.ts";
import type { LineEditor, LineEditorAction } from "./lineEditor.ts";
import { neighbourTimings } from "./subtitle-edit.ts";
import { saveTimings } from "./subtitleEdits.ts";

/** How long after the last nudge the timing saves itself. */
const SETTLE_MS = 500;

/**
 * Saves the edited line's timing by itself: when a drag ends, or shortly after the last nudge,
 * as one timing edit with the neighbours joined to the line. Text and comments wait for Save.
 * Each attempt is its own queued edit; a review batch replays them in order, so only the last one
 * shows. Nothing saves while adding, dragging or [busy], or while another write ([writing]) runs.
 */
export function useTimingAutosave({
  api,
  state,
  cues,
  dragging,
  busy,
  writing,
  dispatch,
  onSaved,
}: {
  api: string;
  state: LineEditor;
  cues: readonly CueView[];
  dragging: boolean;
  busy: boolean;
  writing: RefObject<boolean>;
  dispatch: (action: LineEditorAction) => void;
  onSaved: (saved: { cues: CueView[] }) => void;
}) {
  const saved = useRef(onSaved);
  saved.current = onSaved;
  const { mode, original, times, link } = state;
  useEffect(() => {
    if (mode !== "edit" || dragging || busy) return;
    if (times.start === original.start && times.end === original.end) return;
    let moved: ReturnType<typeof neighbourTimings>;
    try {
      moved = neighbourTimings(cues, original, times, link);
    } catch {
      return;
    }
    const timer = setTimeout(async () => {
      if (writing.current) return;
      writing.current = true;
      dispatch({ type: "timing-status", status: "Saving timing…" });
      try {
        await saveTimings(api, original.id!, times, moved);
        const updated = cues.map((line) => {
          if (line.id === original.id) return { ...line, ...times };
          const neighbour = moved.find((other) => other.id === line.id);
          return neighbour ? { ...line, ...neighbour } : line;
        });
        saved.current({ cues: updated });
        dispatch({
          type: "timing-saved",
          line: { ...original, ...times },
          cues: updated,
        });
      } catch (failure) {
        dispatch({ type: "error", message: (failure as Error).message });
        dispatch({ type: "timing-status", status: "" });
      } finally {
        writing.current = false;
      }
    }, SETTLE_MS);
    return () => clearTimeout(timer);
  }, [
    mode,
    dragging,
    busy,
    times,
    original,
    link,
    cues,
    api,
    writing,
    dispatch,
  ]);
}
