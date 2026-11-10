import { moveTiming, type Timed } from "./model.ts";
import { neighbourTimings, neighbours } from "./subtitle-edit.js";

/** One end of a line. */
export type Boundary = "start" | "end";

/** Whether the line before and the line after move with the edited line's start and end. */
export interface Link {
  previous: boolean;
  next: boolean;
}

/** A subtitle as the timeline draws it. */
export interface TimelineLine extends Timed {
  id?: string;
  source: string;
}

/** Pointer distance, in pixels, within which a boundary of the edited line is grabbed. */
const GRAB = 7;

/**
 * The lines as the timeline shows them while one is edited: [original] at [draft]'s times and
 * text, its linked neighbours moved with it (see `neighbourTimings`), and a line being added
 * slotted in by its start. Throws, as `neighbourTimings` does, if a neighbour would vanish.
 */
export function draftLines<T extends TimelineLine>(
  lines: readonly T[],
  original: T,
  draft: T,
  link: Link,
  adding: boolean,
): T[] {
  if (adding)
    return [...lines, draft].sort((a, b) => a.start - b.start || a.end - b.end);
  const moved = new Map<string, Timed>(
    neighbourTimings(lines, original, draft, link).map(
      ({ id, ...times }: Timed & { id: string }) => [id, times],
    ),
  );
  return lines.map((line) =>
    line.id === original.id
      ? draft
      : line.id && moved.has(line.id)
        ? { ...line, ...moved.get(line.id) }
        : line,
  );
}

/** What a press on the timeline at [x] pixels does. */
export type TimelineHit =
  | { kind: "boundary"; boundary: Boundary }
  | { kind: "move"; offset: number }
  | { kind: "select"; index: number; at: number }
  | { kind: "seek"; at: number };

/**
 * What pressing the timeline does at [x] pixels from its left edge, which shows [viewStart]
 * seconds at [pixelsPerSecond]: near an end of the [selected] line it grabs that boundary, inside
 * that line it grabs the whole line, inside another line it selects it (and seeks there), and
 * elsewhere it seeks.
 */
export function timelineHit(
  lines: readonly Timed[],
  x: number,
  viewStart: number,
  pixelsPerSecond: number,
  selected: number,
): TimelineHit {
  const at = viewStart + x / pixelsPerSecond;
  const line = lines[selected];
  if (line) {
    const distance = (time: number) =>
      Math.abs(x - (time - viewStart) * pixelsPerSecond);
    const nearest: Boundary =
      distance(line.start) <= distance(line.end) ? "start" : "end";
    if (distance(line[nearest]) <= GRAB)
      return { kind: "boundary", boundary: nearest };
    if (at >= line.start && at <= line.end)
      return { kind: "move", offset: at - line.start };
  }
  const index = lines.findIndex((line) => at >= line.start && at < line.end);
  return index >= 0 ? { kind: "select", index, at } : { kind: "seek", at };
}

/**
 * New times for a line whose [boundary] (or whole span, for "move") is dragged to [at] seconds,
 * keeping a fifth of a second of line (or its current length, if shorter), rounded to the
 * millisecond.
 */
export function dragTimes(
  times: Timed,
  mode: Boundary | "move",
  at: number,
  offset = 0,
): Timed {
  const [moved] = moveTiming([times], 0, mode, at, offset);
  const ms = (seconds: number) => Math.round(seconds * 1000) / 1000;
  return { start: ms(moved.start), end: ms(moved.end) };
}

/**
 * Which neighbours move with a drag of [mode] on [original]: those it meets, on the side being
 * dragged, unless [apart] (Alt held) opens a gap. The other side keeps [link].
 */
export function dragLink(
  lines: readonly TimelineLine[],
  original: TimelineLine,
  mode: Boundary | "move",
  apart: boolean,
  link: Link,
): Link {
  const { touching } = neighbours(lines, original);
  return {
    previous: mode === "end" ? link.previous : !apart && touching.previous,
    next: mode === "start" ? link.next : !apart && touching.next,
  };
}
