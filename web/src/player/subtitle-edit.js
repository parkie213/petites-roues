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
