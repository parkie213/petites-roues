export function formatTime(value) {
  const ms = Math.max(0, Math.round(value * 1000));
  const h = Math.floor(ms / 3600000),
    m = Math.floor((ms % 3600000) / 60000),
    s = Math.floor((ms % 60000) / 1000);
  return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}.${String(ms % 1000).padStart(3, "0")}`;
}
export function currentCue(cues, time) {
  for (let i = cues.length - 1; i >= 0; i--)
    // Media seek results can land a microsecond before the requested decimal time.
    // Keep that rounding from briefly mounting the previous subtitle's exercise.
    if (time >= cues[i].start - 0.00001) return i;
  return -1;
}
export function activeCues(cues, time) {
  return cues.filter((cue) => time >= cue.start - 0.00001 && time < cue.end);
}
export function titleInfo(doc) {
  return [
    doc.series,
    doc.season != null
      ? `S${String(doc.season).padStart(2, "0")}${doc.episode != null ? " E" + String(doc.episode).padStart(2, "0") : ""}`
      : "",
    doc.title,
    doc.sourceUrl,
  ]
    .filter(Boolean)
    .join(" · ");
}
export function moveTiming(cues, index, mode, value, offset = 0) {
  const cue = cues[index];
  const minLength = Math.min(0.2, cue.end - cue.start);
  const updated = { ...cue };
  if (mode === "start")
    updated.start = Math.max(0, Math.min(value, cue.end - minLength));
  if (mode === "end") updated.end = Math.max(value, cue.start + minLength);
  if (mode === "move") {
    updated.start = Math.max(0, value - offset);
    updated.end = updated.start + cue.end - cue.start;
  }
  return cues.map((c, i) => (i === index ? updated : c));
}

export function insertGap(answers, index, count) {
  const padded = Array.from({ length: count }, (_, i) => answers[i] || "");
  if (padded.at(-1)?.trim())
    throw Error("Clear the last box before shifting so no word is lost.");
  padded.splice(index, 0, "");
  return padded.slice(0, count);
}
