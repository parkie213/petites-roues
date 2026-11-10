/** Millisecond precision times used by the docked subtitle editor. */
export function editClock(seconds) {
  const ms = Math.round(Math.max(0, seconds) * 1000);
  return `${Math.floor(ms / 60000)}:${String(Math.floor(ms / 1000) % 60).padStart(2, "0")}.${String(ms % 1000).padStart(3, "0")}`;
}
/** Accepts seconds, M:SS.mmm or H:MM:SS.mmm, rejecting malformed boundaries. */
export function editSeconds(text) {
  if (!/^\d+(?::[0-5]\d){0,2}(?:\.\d{1,3})?$/.test(text.trim())) return null;
  return text
    .trim()
    .split(":")
    .reduce((total, part) => total * 60 + Number(part), 0);
}
/** Split only between nonblank text parts and strictly within the edited interval. */
export function splitDraft(draft, cursor, at) {
  const first = draft.source.slice(0, cursor).trim();
  const second = draft.source.slice(cursor).trim();
  if (!first || !second)
    throw Error("Place the French text cursor between the two parts.");
  if (!(at > draft.start && at < draft.end))
    throw Error("Seek to a split time strictly between Start and End.");
  return { at, first, second, start: draft.start, end: draft.end };
}
/**
 * The lines either side of [line] in [cues], and whether each meets it with no gap, so moving
 * their shared boundary should move both by default.
 */
export function neighbours(cues, line) {
  const index = cues.findIndex((cue) => cue.id === line.id);
  const previous = index > 0 ? cues[index - 1] : null;
  const next = index >= 0 ? (cues[index + 1] ?? null) : null;
  return {
    previous,
    next,
    touching: {
      previous: Boolean(previous) && Math.abs(line.start - previous.end) < 0.01,
      next: Boolean(next) && Math.abs(next.start - line.end) < 0.01,
    },
  };
}
/**
 * New timings for the neighbours of [line] that move with it: when [line]'s start moves to
 * [times].start, a linked previous line's end moves by the same amount, and likewise the next
 * line's start with its end, keeping any gap between them. Throws if a neighbour would be left
 * with no time of its own.
 */
export function neighbourTimings(cues, line, times, link) {
  const { previous, next } = neighbours(cues, line);
  const ms = (seconds) => Math.round(seconds * 1000) / 1000;
  const moved = [];
  if (link.previous && previous && times.start !== line.start) {
    const end = ms(previous.end + times.start - line.start);
    if (end <= previous.start)
      throw Error("Start moves past the previous line's start.");
    moved.push({ id: previous.id, start: previous.start, end });
  }
  if (link.next && next && times.end !== line.end) {
    const start = ms(next.start + times.end - line.end);
    if (start >= next.end) throw Error("End moves past the next line's end.");
    moved.push({ id: next.id, start, end: next.end });
  }
  return moved;
}
