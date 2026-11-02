/**
 * Groups library entries into Manga, Series and Films shelves from document metadata only;
 * folder names never appear. Entries arrive in repository order, which breaks ties between
 * copies of the same title.
 *
 * - Manga: comics, grouped by series; a series' slots are its volumes.
 * - Series: videos with a season or episode, grouped by series. A series whose videos have
 *   no numbers at all (a channel such as Le Parisien) is a series too, ordered by title.
 * - Films: other videos, one card each, even when they name a series that has episodes.
 *
 * A slot is one tome/episode/film; copies of it from several repositories or editions share
 * the slot, and the preferred copy is the one whose document and media are present.
 */

const collator = new Intl.Collator(undefined, { numeric: true });

/** Series names match ignoring case, spacing and Unicode composition. */
export const seriesKey = (name) =>
  name.normalize("NFC").trim().replace(/\s+/g, " ").toLocaleLowerCase();

const hasSeries = (entry) => Boolean(entry.series && entry.series.trim());
const numbered = (entry) => entry.season != null || entry.episode != null;
const ready = (entry) =>
  entry.documentAvailable && entry.mediaAvailable
    ? 2
    : entry.documentAvailable
      ? 1
      : 0;

/** Whether the title itself has a card image: its poster, else a comic's first page. */
export const hasTitleArt = (entry) =>
  entry.posterAvailable || (entry.type === "comic" && entry.mediaAvailable);

/** Where a title card leads: reader, watch view or study notes; null if the document is missing. */
export function openHref(entry) {
  if (!entry.documentAvailable) return null;
  const view =
    entry.type === "comic"
      ? "reader"
      : entry.videoAvailable
        ? "watch"
        : "player";
  return `/${view}/${encodeURIComponent(entry.id)}/`;
}

function slotKey(entry) {
  if (entry.type === "comic" && entry.volume != null) return `v${entry.volume}`;
  if (entry.type !== "comic" && numbered(entry))
    return `s${entry.season ?? ""}e${entry.episode ?? ""}`;
  return `d:${entry.documentId}`;
}

function compareSlots(a, b) {
  const x = a.entry,
    y = b.entry;
  return (
    (x.volume ?? Infinity) - (y.volume ?? Infinity) ||
    (x.season ?? 0) - (y.season ?? 0) ||
    (x.episode ?? Infinity) - (y.episode ?? Infinity) ||
    collator.compare(x.title, y.title)
  );
}

function slotLabel(entry) {
  if (entry.type === "comic")
    return entry.volume != null ? `Tome ${entry.volume}` : entry.title;
  return entry.title;
}

function slotDetail(entry) {
  if (entry.type === "comic" || !numbered(entry)) return null;
  return [
    entry.season != null && `S${entry.season}`,
    entry.episode != null && `E${entry.episode}`,
  ]
    .filter(Boolean)
    .join(" · ");
}

function makeSlots(entries) {
  const byKey = new Map();
  for (const entry of entries) {
    const key = slotKey(entry);
    if (!byKey.has(key)) byKey.set(key, []);
    byKey.get(key).push(entry);
  }
  return [...byKey]
    .map(([key, copies]) => {
      // Stable sort: equally ready copies keep repository order.
      const ordered = [...copies].sort((a, b) => ready(b) - ready(a));
      const entry = ordered[0];
      const art = ordered.find(hasTitleArt);
      return {
        key,
        entry,
        copies: ordered,
        label: slotLabel(entry),
        detail: slotDetail(entry),
        art: art ? { id: art.id, kind: "title" } : null,
      };
    })
    .sort(compareSlots);
}

function mostCommon(names) {
  const counts = new Map();
  for (const name of names) counts.set(name, (counts.get(name) || 0) + 1);
  return [...counts].sort((a, b) => b[1] - a[1])[0][0];
}

const plural = (n, word) => `${n} ${word}${n === 1 ? "" : "s"}`;

function seriesGroup(shelf, key, entries) {
  const slots = makeSlots(entries);
  const seasons = [
    ...new Set(slots.map((slot) => slot.entry.season).filter((s) => s != null)),
  ].sort((a, b) => a - b);
  const unit =
    shelf === "manga" ? "tome" : entries.some(numbered) ? "episode" : "video";
  const posterCopy = slots
    .flatMap((slot) => slot.copies)
    .find((entry) => entry.seriesPosterAvailable);
  return {
    key: `${shelf}:${key}`,
    shelf,
    name: mostCommon(entries.map((entry) => entry.series.trim())),
    single: false,
    slots,
    seasons,
    count:
      seasons.length > 1
        ? `${plural(seasons.length, "season")} · ${plural(slots.length, unit)}`
        : plural(slots.length, unit),
    art: posterCopy
      ? { id: posterCopy.id, kind: "series" }
      : (slots.find((slot) => slot.art)?.art ?? null),
  };
}

function singleGroups(shelf, entries) {
  return makeSlots(entries).map((slot) => ({
    key: `${shelf}:${slot.key}`,
    shelf,
    name: slot.entry.title,
    single: true,
    slots: [slot],
    seasons: [],
    count: null,
    art: slot.art,
  }));
}

function bySeries(entries) {
  const groups = new Map();
  for (const entry of entries) {
    const key = seriesKey(entry.series);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(entry);
  }
  return groups;
}

/**
 * Returns the three shelves, each with its cards (groups) sorted by name. Empty shelves are
 * kept so callers can decide whether to show them.
 */
export function shelves(entries) {
  const manga = [],
    series = [],
    films = [];
  const comics = entries.filter((entry) => entry.type === "comic");
  const videos = entries.filter((entry) => entry.type !== "comic");

  for (const [key, group] of bySeries(comics.filter(hasSeries)))
    manga.push(seriesGroup("manga", key, group));
  manga.push(
    ...singleGroups(
      "manga",
      comics.filter((e) => !hasSeries(e)),
    ),
  );

  const loose = videos.filter((entry) => !hasSeries(entry));
  for (const [key, group] of bySeries(videos.filter(hasSeries))) {
    if (!group.some(numbered)) {
      series.push(seriesGroup("series", key, group));
      continue;
    }
    series.push(seriesGroup("series", key, group.filter(numbered)));
    loose.push(...group.filter((entry) => !numbered(entry)));
  }
  films.push(...singleGroups("films", loose));

  const byName = (a, b) => collator.compare(a.name, b.name);
  return [
    { key: "manga", label: "Manga", groups: manga.sort(byName) },
    { key: "series", label: "Series", groups: series.sort(byName) },
    { key: "films", label: "Films", groups: films.sort(byName) },
  ];
}
