import test from "node:test";
import assert from "node:assert/strict";
import {
  mediaEntry,
  namedFromPath,
  openHref,
  shelves,
  warnings,
} from "../src/library/shelves.js";

let next = 0;
function entry(fields) {
  const id = fields.id ?? `title-${++next}`;
  return {
    id,
    documentId: fields.documentId ?? id,
    title: "Untitled",
    series: null,
    season: null,
    episode: null,
    volume: null,
    type: "video",
    documentAvailable: true,
    mediaAvailable: true,
    videoAvailable: fields.type !== "comic",
    posterAvailable: false,
    seriesPosterAvailable: false,
    repositoryId: "one",
    ...fields,
  };
}
const shelf = (result, key) => result.find((s) => s.key === key)?.groups ?? [];
const tome = (volume, fields = {}) =>
  entry({
    type: "comic",
    series: "Demon Slayer",
    title: `Demon Slayer — Tome ${volume}`,
    volume,
    ...fields,
  });
const episode = (season, number, fields = {}) =>
  entry({
    series: "Peppa Pig",
    title: `Episode ${number}`,
    season,
    episode: number,
    ...fields,
  });

test("manga and anime of one series are separate cards; a series film is a film", () => {
  const result = shelves([
    tome(2),
    tome(1),
    tome(10),
    episode(1, 1, { series: "Demon Slayer" }),
    entry({ series: "Demon Slayer", title: "Mugen Train" }),
    entry({ title: "Princesse Mononoké" }),
  ]);
  const [manga] = shelf(result, "comics");
  assert.equal(manga.name, "Demon Slayer");
  assert.equal(manga.count, "3 tomes");
  assert.deepEqual(
    manga.slots.map((s) => s.label),
    ["Tome 1", "Tome 2", "Tome 10"],
  );
  const [anime] = shelf(result, "series");
  assert.equal(anime.name, "Demon Slayer");
  assert.equal(anime.count, "1 episode");
  assert.deepEqual(
    shelf(result, "films").map((g) => [g.name, g.single]),
    [
      ["Mugen Train", true],
      ["Princesse Mononoké", true],
    ],
  );
});

test("episodes sort by season then episode, and seasons are counted only when several", () => {
  let [peppa] = shelf(
    shelves([episode(1, 2), episode(1, 10), episode(1, 1)]),
    "series",
  );
  assert.equal(peppa.count, "3 episodes");
  assert.deepEqual(
    peppa.slots.map((s) => s.detail),
    ["S1 · E1", "S1 · E2", "S1 · E10"],
  );
  [peppa] = shelf(
    shelves([episode(2, 1), episode(1, 1), episode(1, 2)]),
    "series",
  );
  assert.deepEqual(peppa.seasons, [1, 2]);
  assert.equal(peppa.count, "2 seasons · 3 episodes");
  assert.deepEqual(
    peppa.slots.map((s) => s.detail),
    ["S1 · E1", "S1 · E2", "S2 · E1"],
  );
});

test("a series without numbers, such as a channel, stays a series of videos", () => {
  const [parisien] = shelf(
    shelves([
      entry({ series: "Le Parisien", title: "Paris : cette boulangerie" }),
      entry({ series: "Le Parisien", title: "Des frigos piratés" }),
    ]),
    "series",
  );
  assert.equal(parisien.count, "2 videos");
  assert.deepEqual(
    parisien.slots.map((s) => s.label),
    ["Des frigos piratés", "Paris : cette boulangerie"],
  );
});

test("two repositories merge into one series; the same episode becomes one slot with copies", () => {
  const result = shelves([
    episode(1, 1, {
      repositoryId: "one",
      mediaAvailable: false,
      videoAvailable: false,
    }),
    episode(1, 2, { repositoryId: "one", series: "Peppa  pig" }),
    episode(1, 1, { repositoryId: "two" }),
    episode(2, 1, { repositoryId: "two", series: "peppa pig " }),
  ]);
  const groups = shelf(result, "series");
  assert.equal(groups.length, 1);
  const [peppa] = groups;
  assert.equal(peppa.name, "Peppa Pig", "the most common spelling is shown");
  assert.equal(peppa.slots.length, 3);
  const first = peppa.slots[0];
  assert.equal(first.copies.length, 2);
  assert.equal(
    first.entry.repositoryId,
    "two",
    "the copy with media is preferred",
  );
  assert.equal(openHref(first.entry), `/watch/${first.entry.id}/`);
});

test("equally available copies keep repository order", () => {
  const [film] = shelf(
    shelves([
      entry({
        id: "a",
        documentId: "robots",
        title: "ROBOTS",
        repositoryId: "one",
      }),
      entry({
        id: "b",
        documentId: "robots",
        title: "ROBOTS",
        repositoryId: "two",
      }),
    ]),
    "films",
  );
  assert.equal(film.slots[0].copies.length, 2);
  assert.equal(film.slots[0].entry.id, "a");
});

test("card art: series poster, else first slot with art, else placeholder", () => {
  let [manga] = shelf(shelves([tome(2), tome(1, { id: "t1" })]), "comics");
  assert.deepEqual(manga.art, { url: "/api/titles/t1/card" });
  assert.deepEqual(manga.slots[0].art, { url: "/api/titles/t1/card" });

  [manga] = shelf(
    shelves([tome(1), tome(2, { id: "t2", seriesPosterAvailable: true })]),
    "comics",
  );
  assert.deepEqual(manga.art, { url: "/api/titles/t2/series-card" });

  const [peppa] = shelf(shelves([episode(1, 1), episode(1, 2)]), "series");
  assert.equal(peppa.art, null, "videos have no art without a poster");
  const [film] = shelf(
    shelves([entry({ id: "f", title: "Film", posterAvailable: true })]),
    "films",
  );
  assert.deepEqual(film.art, { url: "/api/titles/f/card" });
});

test("titles open the reader, watch view or notes; missing documents do not open", () => {
  assert.equal(openHref(tome(1, { id: "c" })), "/reader/c/");
  assert.equal(
    openHref(entry({ id: "v", videoAvailable: false, mediaAvailable: false })),
    "/player/v/",
  );
  assert.equal(openHref(entry({ documentAvailable: false })), null);
});

test("bare media is named from its path the way media servers read it", () => {
  const named = (path) => {
    const { series, season, episode, title } = namedFromPath(path);
    return [series, season, episode, title];
  };
  assert.deepEqual(named("peppa-pig/s01e01-les-flaques-de-boue.mp4"), [
    "Peppa pig",
    1,
    1,
    "Les flaques de boue",
  ]);
  assert.deepEqual(named("masha-and-the-bear/083-bienenalarm.de.mp4"), [
    "Masha and the bear",
    null,
    83,
    "Bienenalarm",
  ]);
  assert.deepEqual(named("Show/Season 02/Show - E05.mkv"), [
    "Show",
    2,
    null,
    "Show E05",
  ]);
  assert.deepEqual(named("peppa-pig/s01e03-ma-meilleure-amie/video.mp4"), [
    "Peppa pig",
    1,
    3,
    "Ma meilleure amie",
  ]);
  assert.deepEqual(named("Le Chateau Ambulant.mkv"), [
    null,
    null,
    null,
    "Le Chateau Ambulant",
  ]);
  assert.deepEqual(named("films/2046.mkv"), ["Films", null, null, "2046"]);
});

const bare = (fields = {}) =>
  mediaEntry({
    repositoryId: "one",
    relativePath: "peppa-pig/s01e01-les-flaques-de-boue.mp4",
    kind: "video",
    fingerprint: "abc",
    durationSeconds: 62,
    posterAvailable: false,
    seriesPosterAvailable: true,
    warnings: ["No study document"],
    ...fields,
  });

test("bare media joins its series beside titles with documents, flagged", () => {
  const linked = entry({
    series: "Peppa Pig",
    season: 1,
    episode: 2,
    title: "Mr Dinosaure",
  });
  const [group] = shelf(shelves([linked, bare()]), "series");
  assert.equal(group.name, "Peppa Pig");
  assert.equal(group.slots.length, 2);
  const episode = group.slots[0].entry;
  assert.equal(openHref(episode), "/video/abc/", "a bare video still plays");
  assert.deepEqual(warnings(episode), ["No study document"]);
  assert.deepEqual(group.art, { url: "/api/media/abc/series-card" });
});

test("a bare film uses its folder poster, and a bare comic its own card", () => {
  const [film] = shelf(shelves([bare({ relativePath: "Ponyo.mkv" })]), "films");
  assert.deepEqual(film.art, { url: "/api/media/abc/series-card" });
  const [comic] = shelf(
    shelves([bare({ relativePath: "volume.cbz", kind: "comic" })]),
    "comics",
  );
  assert.deepEqual(comic.art, { url: "/api/media/abc/card" });
  assert.equal(
    openHref(comic.slots[0].entry),
    null,
    "a comic needs its document",
  );
});

test("a document can name its shelf; a series sits where most of its titles say", () => {
  const result = shelves([
    tome(1, { shelf: "Manga" }),
    tome(2, { shelf: "Manga" }),
    tome(3),
    entry({ type: "comic", title: "Lulu" }),
    entry({ title: "Ponyo", shelf: " Ghibli " }),
  ]);
  assert.deepEqual(
    result.map((s) => [s.label, s.groups.map((g) => g.name)]),
    [
      ["Comics", ["Lulu"]],
      ["Ghibli", ["Ponyo"]],
      ["Manga", ["Demon Slayer"]],
    ],
  );
  const [series] = shelf(result, "manga");
  assert.equal(
    series.count,
    "3 tomes",
    "the unshelved tome stays with its series",
  );
  assert.equal(series.key, "manga:demon slayer");
  assert.equal(series.type, "comic");
});
