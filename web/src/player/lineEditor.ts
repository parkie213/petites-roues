import type { CueView } from "../shared/types.ts";
import { lineChanged, neighbours } from "./subtitle-edit.ts";
import type { Draft, Times } from "./subtitle-edit.ts";
import type { Boundary, Link } from "./timeline.ts";

/** Editing a saved line, or adding a new one. */
export type EditMode = "edit" | "add";

/** A line being edited or added: as saved ([original]) and as drafted. */
export interface LineEditor {
  mode: EditMode;
  /** The line as last saved, or a new line as first placed. */
  original: Draft & Times;
  /** The saved line the Edit tab returns to from Add, if any. */
  editing: (Draft & Times) | null;
  draft: Draft;
  times: Times;
  link: Link;
  picked: Boundary;
  comment: string;
  error: string;
  /** Whether the delete confirmation shows. */
  deleting: boolean;
  /** What timing's own saving last said. */
  timingStatus: string;
}

export type LineEditorAction =
  /** Starts editing (or adding) [line], one of [cues]: everything else starts afresh. */
  | {
      type: "load";
      line: Draft & Times;
      cues: readonly CueView[];
      as?: EditMode;
    }
  | { type: "draft"; changes: Partial<Draft> }
  /** The line's times as a drag, nudge or key moved them, and which neighbours move along. */
  | { type: "retime"; times: Times; link: Link }
  | { type: "pick"; boundary: Boundary }
  | { type: "comment"; text: string }
  | { type: "error"; message: string }
  | { type: "deleting"; asking: boolean }
  | { type: "timing-status"; status: string }
  /** The timing saved itself: [line] is the line as saved, among [cues]. */
  | { type: "timing-saved"; line: Draft & Times; cues: readonly CueView[] };

/** The editor opening on [line], one of [cues]: as added if [as] is "add". */
export function openLine(
  line: Draft & Times,
  cues: readonly CueView[],
  as: EditMode = "edit",
  editing: (Draft & Times) | null = as === "edit" ? line : null,
): LineEditor {
  return {
    mode: as,
    original: line,
    editing,
    draft: line,
    times: { start: line.start, end: line.end },
    link: neighbours(cues, line).touching,
    picked: "end",
    comment: "",
    error: "",
    deleting: false,
    timingStatus: "",
  };
}

export function lineEditor(
  state: LineEditor,
  action: LineEditorAction,
): LineEditor {
  switch (action.type) {
    case "load": {
      const as = action.as ?? "edit";
      const next = openLine(
        action.line,
        action.cues,
        as,
        as === "edit" ? action.line : state.editing,
      );
      // The picked end is kept from line to line.
      return { ...next, picked: state.picked };
    }
    case "draft":
      return { ...state, draft: { ...state.draft, ...action.changes } };
    case "retime":
      return { ...state, times: action.times, link: action.link };
    case "pick":
      return { ...state, picked: action.boundary };
    case "comment":
      return { ...state, comment: action.text };
    case "error":
      return { ...state, error: action.message };
    case "deleting":
      return { ...state, deleting: action.asking };
    case "timing-status":
      return { ...state, timingStatus: action.status };
    case "timing-saved":
      return {
        ...state,
        original: action.line,
        editing: action.line,
        link: neighbours(action.cues, action.line).touching,
        timingStatus: "Timing saved",
      };
  }
}

/** Whether the line's text or times differ from it as saved. */
export const changed = (state: LineEditor) =>
  lineChanged(state.original, state.draft, state.times);

/** Whether leaving would lose something: a change, a comment, or a new line's text. */
export const dirty = (state: LineEditor) =>
  changed(state) ||
  Boolean(state.comment.trim()) ||
  (state.mode === "add" && Boolean(state.draft.source.trim()));
