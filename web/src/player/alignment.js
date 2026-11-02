export const alignmentColors = [
  "#89c9b3",
  "#b8a5ed",
  "#e8b97e",
  "#8dbde5",
  "#e89dac",
  "#bdcd85",
  "#91cbd2",
];

// An annotation is usable only while its complete source and translation still match.
// Span indices refer to phrases, not words: idioms and split expressions stay together.
export function validAlignment(cue, candidate) {
  if (
    !cue ||
    !candidate ||
    candidate.sourceText !== cue.source ||
    candidate.translationText !== cue.translation ||
    !Array.isArray(candidate.source) ||
    !Array.isArray(candidate.translation) ||
    !Array.isArray(candidate.links) ||
    !candidate.links.length
  )
    return null;
  for (const side of ["source", "translation"]) {
    if (
      !candidate[side].length ||
      !candidate[side].every(
        (phrase) => typeof phrase === "string" && phrase.trim(),
      ) ||
      candidate[side].join(" ") !== cue[side]
    )
      return null;
  }
  const covered = { source: new Set(), translation: new Set() };
  for (const link of candidate.links) {
    if (!link || typeof link.note !== "string" || !link.note.trim())
      return null;
    for (const side of ["source", "translation"]) {
      if (
        !Array.isArray(link[side]) ||
        !link[side].every(
          (index) =>
            Number.isInteger(index) &&
            index >= 0 &&
            index < candidate[side].length,
        ) ||
        new Set(link[side]).size !== link[side].length
      )
        return null;
      link[side].forEach((index) => covered[side].add(index));
    }
    if (!link.source.length && !link.translation.length) return null;
  }
  // Nothing is silently dropped, including words with no separate equivalent.
  if (
    covered.source.size !== candidate.source.length ||
    covered.translation.size !== candidate.translation.length
  )
    return null;
  return candidate;
}

export function alignmentState(cue) {
  if (cue?.alignmentStale) return "stale";
  if (!cue?.alignment) return "missing";
  return validAlignment(cue, cue.alignment) ? "ready" : "stale";
}

// A notes review may be complete while phrase regeneration is still outstanding.
export function pendingReviewCount(changes, cues) {
  const pending = changes.filter((change) => !change.reviewedAt);
  const queued = new Set(pending.flatMap((change) => change.cueIds));
  return (
    pending.length +
    cues.filter((cue) => alignmentState(cue) === "stale" && !queued.has(cue.id))
      .length
  );
}

// Shared links may map several source phrases onto the same translated words.
export function phraseTranslation(alignment, sourceIndex) {
  const links = alignment.links.filter((link) =>
    link.source.includes(sourceIndex),
  );
  const indices = new Set(links.flatMap((link) => link.translation));
  let previous = -1;
  return alignment.translation
    .flatMap((phrase, index) => {
      if (!indices.has(index)) return [];
      const prefix = previous < 0 ? "" : index === previous + 1 ? " " : " … ";
      previous = index;
      return [prefix + phrase];
    })
    .join("");
}

// The whole translated line in its own word order, each chunk tagged with the link that colours
// its source phrase and whether it belongs to the phrase being explored.
export function translationContext(alignment, sourceIndex) {
  const active = new Set(
    alignment.links
      .filter((link) => link.source.includes(sourceIndex))
      .flatMap((link) => link.translation),
  );
  return alignment.translation.map((text, index) => ({
    text,
    link: alignment.links.findIndex((link) => link.translation.includes(index)),
    active: active.has(index),
  }));
}
