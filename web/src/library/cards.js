/**
 * What a Posters card does with its entry. The server groups entries into shelves and cards
 * (GET /api/library/posters); these helpers only read an entry it sent.
 */

/** Everything that needs the user on one entry, in display order. */
export const warnings = (entry) => Object.values(entry.unavailable ?? {});

/**
 * Where a card leads: reader, watch view or study notes; media without a study document plays or
 * reads on its own. Null when there is nothing to open (a title whose document is missing).
 */
export function openHref(entry) {
  if (entry.fingerprint)
    return `/${entry.type === "comic" ? "comic" : "video"}/${encodeURIComponent(entry.fingerprint)}/`;
  if (!entry.documentAvailable) return null;
  const view =
    entry.type === "comic"
      ? "reader"
      : entry.videoAvailable
        ? "watch"
        : "player";
  return `/${view}/${encodeURIComponent(entry.id)}/`;
}
