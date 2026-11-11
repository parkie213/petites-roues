import type { CueView } from "../shared/types.ts";
import type { Timed } from "./model.ts";
import type { Link } from "./timeline.ts";

/** A line's place in time, as the editor drafts it. */
export type Times = Timed;

/** A timed line with an id, as the editor's timing works on. */
export type IdentifiedTimes = Times & { id?: string };

/** Millisecond precision times used by the docked subtitle editor. */
export function editClock(seconds: number) {
  const ms = Math.round(Math.max(0, seconds) * 1000);
  return `${Math.floor(ms / 60000)}:${String(Math.floor(ms / 1000) % 60).padStart(2, "0")}.${String(ms % 1000).padStart(3, "0")}`;
}
/** Accepts seconds, M:SS.mmm or H:MM:SS.mmm, rejecting malformed boundaries. */
export function editSeconds(text: string) {
  if (!/^\d+(?::[0-5]\d){0,2}(?:\.\d{1,3})?$/.test(text.trim())) return null;
  return text
    .trim()
    .split(":")
    .reduce((total, part) => total * 60 + Number(part), 0);
}

/** Where to split a line: the two texts and the time between them. */
export interface Split {
  at: number;
  first: string;
  second: string;
  start: number;
  end: number;
}

/**
 * [draft] split at the text [cursor] and the time [at]: only between nonblank text parts and
 * strictly within its times. [sourceName] names the text's language in the error.
 */
export function splitDraft(
  draft: Times & { source: string },
  cursor: number,
  at: number,
  sourceName = "source",
): Split {
  const first = draft.source.slice(0, cursor).trim();
  const second = draft.source.slice(cursor).trim();
  if (!first || !second)
    throw Error(`Place the ${sourceName} text cursor between the two parts.`);
  if (!(at > draft.start && at < draft.end))
    throw Error("Seek to a split time strictly between Start and End.");
  return { at, first, second, start: draft.start, end: draft.end };
}
/**
 * The lines either side of [line] in [cues], and whether each meets it with no gap, so moving
 * their shared boundary should move both by default.
 */
export function neighbours<T extends IdentifiedTimes>(
  cues: readonly T[],
  line: IdentifiedTimes,
) {
  const index = cues.findIndex((cue) => cue.id === line.id);
  const previous = index > 0 ? cues[index - 1] : null;
  const next = index >= 0 ? (cues[index + 1] ?? null) : null;
  return {
    previous,
    next,
    touching: {
      previous:
        Boolean(previous) && Math.abs(line.start - previous!.end) < 0.01,
      next: Boolean(next) && Math.abs(next!.start - line.end) < 0.01,
    } satisfies Link,
  };
}
/**
 * New timings for the neighbours of [line] joined to it: a linked previous line ends at
 * [times].start, and a linked next line starts at [times].end, closing any gap between them.
 * Throws if a neighbour would be left with no time of its own.
 */
export function neighbourTimings(
  cues: readonly IdentifiedTimes[],
  line: IdentifiedTimes,
  times: Times,
  link: Link,
) {
  const { previous, next } = neighbours(cues, line);
  const ms = (seconds: number) => Math.round(seconds * 1000) / 1000;
  const moved: (Times & { id: string })[] = [];
  if (link.previous && previous && times.start !== previous.end) {
    const end = ms(times.start);
    if (end <= previous.start)
      throw Error("Start moves past the previous line's start.");
    moved.push({ id: previous.id!, start: previous.start, end });
  }
  if (link.next && next && times.end !== next.start) {
    const start = ms(times.end);
    if (start >= next.end) throw Error("End moves past the next line's end.");
    moved.push({ id: next.id!, start, end: next.end });
  }
  return moved;
}

/** A line as the editor drafts it: a saved cue's fields, or a new line's. */
export type Draft = Partial<CueView> & {
  source: string;
  translation?: string | null;
};

/**
 * A new line at the playhead [at]: two seconds long, unless the next line in [cues] or the end of
 * the video ([duration], if known) comes sooner.
 */
export function blankLine(
  cues: readonly Times[],
  at: number,
  duration: number | undefined,
): Draft & Times {
  const next = cues.find((line) => line.start > at);
  return {
    start: at,
    end: Math.min(at + 2, next?.start ?? at + 2, duration || Infinity),
    source: "",
    translation: "",
    notes: [],
  };
}

/** Whether [draft] and [times] differ from [original] in what a save writes. */
export function lineChanged(
  original: Draft & Times,
  draft: Draft,
  times: Times,
) {
  return (
    draft.source !== original.source ||
    (draft.translation || "") !== (original.translation || "") ||
    times.start !== original.start ||
    times.end !== original.end
  );
}

/** The line a save sends: [draft] at [times], its text trimmed and empty fields made explicit. */
export function linePayload(draft: Draft, times: Times) {
  return {
    ...draft,
    ...times,
    source: draft.source.trim(),
    translation: draft.translation?.trim() || null,
    notes: draft.notes || [],
    continuesPrevious: Boolean(draft.continuesPrevious),
    listeningCheck: draft.listeningCheck ?? null,
    textCheck: draft.textCheck ?? null,
  };
}

/**
 * The line to edit after deleting or splitting [original], which was in [before]: the split's
 * first part, or the line that took the deleted one's place (else the one before it). Null when
 * none is left.
 */
export function lineAfter<T extends IdentifiedTimes>(
  operation: "delete" | "split",
  before: readonly IdentifiedTimes[],
  original: IdentifiedTimes,
  after: readonly T[],
): T | null {
  if (operation === "split")
    return after.find((line) => line.start === original.start) ?? null;
  const index = before.findIndex((line) => line.id === original.id);
  return after[index] ?? after[index - 1] ?? null;
}
