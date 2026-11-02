// Selection is based on visible transcript order; modifier clicks never seek.
export function selectTranscriptRows(
  ids,
  selected,
  anchor,
  clicked,
  { toggle = false, range = false } = {},
) {
  if (range && ids.includes(anchor)) {
    const a = ids.indexOf(anchor),
      b = ids.indexOf(clicked);
    return new Set(ids.slice(Math.min(a, b), Math.max(a, b) + 1));
  }
  if (toggle) {
    const next = new Set(selected);
    next.has(clicked) ? next.delete(clicked) : next.add(clicked);
    return next;
  }
  return new Set();
}
