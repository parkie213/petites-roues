// Start handles occupy the top lane; end handles occupy the lower lane.
// Adjacent cues can therefore share a boundary without hiding either handle.
export function timingHit(cues, point, start, pixelsPerSecond, selected = -1) {
  if (point.y < 1 || point.y > 39 || pixelsPerSecond <= 0) return null;
  const mode = point.y < 20 ? "start" : "end";
  const edges = cues
    .map((cue, index) => ({
      index,
      mode,
      distance: Math.abs(point.x - (cue[mode] - start) * pixelsPerSecond),
    }))
    .filter((hit) => hit.distance <= 8)
    .sort(
      (a, b) =>
        a.distance - b.distance ||
        Number(b.index === selected) - Number(a.index === selected),
    );
  if (edges.length) return { index: edges[0].index, mode };
  const at = start + point.x / pixelsPerSecond;
  const chosen = cues[selected];
  const index =
    chosen && at >= chosen.start && at <= chosen.end
      ? selected
      : cues.findIndex((cue) => at >= cue.start && at <= cue.end);
  return index < 0
    ? null
    : { index, mode: "move", offset: at - cues[index].start };
}
