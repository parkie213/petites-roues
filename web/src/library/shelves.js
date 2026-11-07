/**
 * Groups library entries into shelves from document metadata. A document may name its shelf
 * (`shelf`, any label such as "Manga"); otherwise it goes on a default shelf for its kind. Entries
 * arrive in repository order, which breaks ties between copies of the same title.
 *
 * - Comics: comics, grouped by series; a series' slots are its volumes.
 * - Series: videos with a season or episode, grouped by series. A series whose videos have
 *   no numbers at all (a channel such as Le Parisien) is a series too, ordered by title.
 * - Films: other videos, one card each, even when they name a series that has episodes.
 *
 * A series in several source languages is one card per language: a learner follows one language's
 * episodes, so French and German Peppa Pig would be separate cards. Media without a study document has
 * no language; it joins its series' card when that series has only one language.
 *
 * A series card sits on the shelf most of its titles name, so one stray volume does not split
 * the series. Shelves are ordered by label.
 *
 * A slot is one tome/episode/film; copies of it from several repositories or folders share
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

const EPISODE = /s(\d{1,3})\s*e(\d{1,4})/i;
const SEASON_FOLDER = /^(?:season|saison|staffel)[\s._-]*(\d{1,3})$/i;
/** A trailing language tag on a file name, as in "lunch.zh" or "film.pt-BR". */
const LANGUAGE_TAG = /\.[a-z]{2,3}(?:-[a-z0-9]{2,8})?$/i;

/** "les-flaques_de.boue" → "Les flaques de boue". */
function readable(name) {
  const words = name
    .replace(/[-_.]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return words.charAt(0).toLocaleUpperCase() + words.slice(1);
}

/**
 * What a bare file's path says, the way media servers read it: the series is the nearest folder
 * that is not a season or episode folder; season and episode come from "s01e02" or a "Season 1"
 * folder; a leading number in a series folder is the episode ("083-bienenalarm"). For display
 * only — linking to documents is by fingerprint alone.
 */
export function namedFromPath(relativePath) {
  const parts = relativePath.split("/");
  const file = parts.pop();
  const stem = file.replace(/\.[^.]+$/, "").replace(LANGUAGE_TAG, "");
  let season = null,
    episode = null,
    series = null,
    episodeFolder = null;
  for (const folder of parts.reverse()) {
    const seasonFolder = folder.match(SEASON_FOLDER);
    if (seasonFolder) season ??= Number(seasonFolder[1]);
    else if (EPISODE.test(folder)) episodeFolder ??= folder;
    else {
      series = readable(folder);
      break;
    }
  }
  // An episode folder ("s01e01-les-flaques/video.mp4") names the episode better than its file.
  let rest = EPISODE.test(stem) || !episodeFolder ? stem : episodeFolder;
  const coded = rest.match(EPISODE);
  if (coded) {
    season = Number(coded[1]);
    episode = Number(coded[2]);
    rest = rest.replace(EPISODE, "");
  } else if (series) {
    const leading = stem.match(/^(\d{1,4})(?=[\s._-])/);
    if (leading) {
      episode = Number(leading[1]);
      rest = stem.slice(leading[0].length);
    }
  }
  return { series, season, episode, title: readable(rest) || readable(stem) };
}

/**
 * A media file no study document references, as a card entry: named from its path, carrying its
 * warnings. A video plays on its own; a comic cannot be opened until it has a document. A file whose path names a series and
 * episode joins that series' card, beside any titles that have documents.
 */
export function mediaEntry(item) {
  const id = `media:${item.repositoryId}/${item.relativePath}`;
  return {
    ...namedFromPath(item.relativePath),
    id,
    documentId: id,
    volume: null,
    type: item.kind === "comic" ? "comic" : "video",
    repositoryId: item.repositoryId,
    relativePath: item.relativePath,
    durationSeconds: item.durationSeconds,
    fingerprint: item.fingerprint,
    cardBase: `/api/media/${encodeURIComponent(item.fingerprint)}`,
    documentAvailable: false,
    mediaAvailable: true,
    videoAvailable: false,
    posterAvailable: item.posterAvailable,
    seriesPosterAvailable: item.seriesPosterAvailable,
    unavailable: Object.fromEntries(
      item.warnings.map((warning, index) => [`media-${index}`, warning]),
    ),
  };
}

/** Where an entry's card images are served: by title id, or by fingerprint for bare media. */
const cardBase = (entry) =>
  entry.cardBase ?? `/api/titles/${encodeURIComponent(entry.id)}`;
const titleArt = (entry) => ({ url: `${cardBase(entry)}/card` });
const seriesArt = (entry) => ({ url: `${cardBase(entry)}/series-card` });

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

/** Copies share a slot only within one language: another language's tome 1 is not a copy. */
function slotKey(entry) {
  const language = entry.sourceLanguage ? `${entry.sourceLanguage}:` : "";
  if (entry.type === "comic" && entry.volume != null)
    return `${language}v${entry.volume}`;
  if (entry.type !== "comic" && numbered(entry))
    return `${language}s${entry.season ?? ""}e${entry.episode ?? ""}`;
  return `d:${entry.documentId}`;
}

/** The language most of the entries declare, or null when none declares one. */
function languageOf(entries) {
  const tags = entries.map((entry) => entry.sourceLanguage).filter(Boolean);
  return tags.length ? mostCommon(tags) : null;
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
        art: art ? titleArt(art) : null,
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

function seriesGroup(type, key, entries) {
  const slots = makeSlots(entries);
  const seasons = [
    ...new Set(slots.map((slot) => slot.entry.season).filter((s) => s != null)),
  ].sort((a, b) => a - b);
  const unit =
    type === "comic" ? "tome" : entries.some(numbered) ? "episode" : "video";
  const posterCopy = slots
    .flatMap((slot) => slot.copies)
    .find((entry) => entry.seriesPosterAvailable);
  return {
    key,
    type,
    language: languageOf(entries),
    name: mostCommon(entries.map((entry) => entry.series.trim())),
    single: false,
    slots,
    seasons,
    count:
      seasons.length > 1
        ? `${plural(seasons.length, "season")} · ${plural(slots.length, unit)}`
        : plural(slots.length, unit),
    art: posterCopy
      ? seriesArt(posterCopy)
      : (slots.find((slot) => slot.art)?.art ?? null),
  };
}

/** A film in its own folder often has only that folder's poster, which is its series poster. */
function folderArt(slot) {
  const copy = slot.copies.find((entry) => entry.seriesPosterAvailable);
  return copy ? seriesArt(copy) : null;
}

function singleGroups(type, entries) {
  return makeSlots(entries).map((slot) => ({
    key: slot.key,
    type,
    language: languageOf(slot.copies),
    name: slot.entry.title,
    single: true,
    slots: [slot],
    seasons: [],
    count: null,
    art: slot.art ?? folderArt(slot),
  }));
}

/**
 * Groups entries by series and source language. Entries without a language (media awaiting a study
 * document) join their series' only language; with several, they keep a card of their own.
 */
function bySeries(entries) {
  const languages = new Map();
  for (const entry of entries) {
    const key = seriesKey(entry.series);
    if (!languages.has(key)) languages.set(key, new Set());
    if (entry.sourceLanguage) languages.get(key).add(entry.sourceLanguage);
  }
  const groups = new Map();
  for (const entry of entries) {
    const series = seriesKey(entry.series);
    const known = languages.get(series);
    const language =
      entry.sourceLanguage ?? (known.size === 1 ? [...known][0] : null);
    const key = language ? `${series}@${language}` : series;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(entry);
  }
  return groups;
}

/** The shelf a group belongs on: the label most of its titles name, else its default. */
function shelfOf(group, fallback) {
  const labels = group.slots
    .flatMap((slot) => slot.copies)
    .map((entry) => entry.shelf?.trim() || fallback);
  return mostCommon(labels);
}

/**
 * Returns the shelves that have cards, ordered by label, each with its cards (groups) sorted by
 * name. A group's key is unique across shelves: its shelf, then its series or slot.
 */
export function shelves(entries) {
  const placed = [];
  const place = (fallback) => (group) =>
    placed.push([shelfOf(group, fallback), group]);
  const comics = entries.filter((entry) => entry.type === "comic");
  const videos = entries.filter((entry) => entry.type !== "comic");

  for (const [key, group] of bySeries(comics.filter(hasSeries)))
    place("Comics")(seriesGroup("comic", key, group));
  singleGroups(
    "comic",
    comics.filter((e) => !hasSeries(e)),
  ).forEach(place("Comics"));

  const loose = videos.filter((entry) => !hasSeries(entry));
  for (const [key, group] of bySeries(videos.filter(hasSeries))) {
    if (!group.some(numbered)) {
      place("Series")(seriesGroup("video", key, group));
      continue;
    }
    place("Series")(seriesGroup("video", key, group.filter(numbered)));
    loose.push(...group.filter((entry) => !numbered(entry)));
  }
  singleGroups("video", loose).forEach(place("Films"));

  const byKey = new Map();
  for (const [label, group] of placed) {
    const key = seriesKey(label);
    if (!byKey.has(key)) byKey.set(key, { key, labels: [], groups: [] });
    const shelf = byKey.get(key);
    shelf.labels.push(label);
    shelf.groups.push({ ...group, key: `${key}:${group.key}`, shelf: key });
  }
  const byName = (a, b) => collator.compare(a.name, b.name);
  return [...byKey.values()]
    .map(({ key, labels, groups }) => ({
      key,
      label: mostCommon(labels),
      groups: groups.sort(byName),
    }))
    .sort((a, b) => collator.compare(a.label, b.label));
}
