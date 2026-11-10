const plural = (n, word) => `${n} ${word}${n === 1 ? "" : "s"}`;

/**
 * What the library's posters hold, counting media with and without a study document: "2 videos ·
 * 1 comic", how many cards show a (!) as "3 warnings", and how many study documents found no media
 * (listed only in the Documents view). Null for an empty library.
 */
export function librarySummary(repos) {
  const all = repos.flatMap((repo) => repo.entries);
  const titles = all.filter((entry) => entry.mediaAvailable);
  const unlinked = all.length - titles.length;
  const media = repos
    .flatMap((repo) => repo.media ?? [])
    .filter((item) => !item.titleId);
  const comics =
    titles.filter((entry) => entry.type === "comic").length +
    media.filter((item) => item.kind === "comic").length;
  const videos = titles.length + media.length - comics;
  const attention =
    media.length +
    titles.filter((entry) => Object.keys(entry.unavailable ?? {}).length > 0)
      .length;
  const counts = [
    videos > 0 && plural(videos, "video"),
    comics > 0 && plural(comics, "comic"),
  ].filter(Boolean);
  if (counts.length === 0 && unlinked === 0) return null;
  return {
    counts: counts.join(" · "),
    unlinked:
      unlinked > 0
        ? `${plural(unlinked, "study document")} without media`
        : null,
    attention: attention > 0 ? plural(attention, "warning") : null,
  };
}
