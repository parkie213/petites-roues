/**
 * What a Posters card does with its entry. The server groups entries into shelves and cards
 * (GET /api/library/posters); these helpers only read an entry it sent.
 */

/** Everything that needs the user on one entry, in display order. */
export const warnings = (entry) => Object.values(entry.unavailable ?? {});

/**
 * Where a card leads: reader, watch view or study notes; a video without a study document plays on
 * its own. Null when there is nothing to open (a missing document, or a comic without one).
 */
export function openHref(entry) {
  if (entry.fingerprint && entry.type === "video")
    return `/video/${encodeURIComponent(entry.fingerprint)}/`;
  if (!entry.documentAvailable) return null;
  const view =
    entry.type === "comic"
      ? "reader"
      : entry.videoAvailable
        ? "watch"
        : "player";
  return `/${view}/${encodeURIComponent(entry.id)}/`;
}
