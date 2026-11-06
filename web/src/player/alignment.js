export const alignmentColors = [
  "#89c9b3",
  "#b8a5ed",
  "#e8b97e",
  "#8dbde5",
  "#e89dac",
  "#bdcd85",
  "#91cbd2",
];

// An annotation is usable only while its chunks still spell out the line's complete source and
// translation. Span indices refer to phrases, not words: idioms and split expressions stay together.
export function validAlignment(cue, candidate) {
  if (
    !cue ||
    !candidate ||
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
    // Notes are optional: a breakdown is shown before the notes stage explains it.
    if (!link || (link.note != null && typeof link.note !== "string"))
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

/**
 * "ready" when the line has a usable phrase breakdown, else "missing": never made, or cleared by
 * an edit of its wording. The server only serves valid breakdowns; the check guards the display.
 */
export function alignmentState(cue) {
  return cue?.alignment && validAlignment(cue, cue.alignment)
    ? "ready"
    : "missing";
}

/** Player and reader edits still waiting for the reviewing agent. */
export function pendingReviewCount(changes) {
  return changes.filter((change) => !change.reviewedAt).length;
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
