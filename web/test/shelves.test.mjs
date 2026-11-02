import test from "node:test";
import assert from "node:assert/strict";
import { openHref, shelves } from "../src/library/shelves.js";

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
const shelf = (result, key) => result.find((s) => s.key === key).groups;
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
  const [manga] = shelf(result, "manga");
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
  let [manga] = shelf(shelves([tome(2), tome(1, { id: "t1" })]), "manga");
  assert.deepEqual(manga.art, { id: "t1", kind: "title" });
  assert.deepEqual(manga.slots[0].art, { id: "t1", kind: "title" });

  [manga] = shelf(
    shelves([tome(1), tome(2, { id: "t2", seriesPosterAvailable: true })]),
    "manga",
  );
  assert.deepEqual(manga.art, { id: "t2", kind: "series" });

  const [peppa] = shelf(shelves([episode(1, 1), episode(1, 2)]), "series");
  assert.equal(peppa.art, null, "videos have no art without a poster");
  const [film] = shelf(
    shelves([entry({ id: "f", title: "Film", posterAvailable: true })]),
    "films",
  );
  assert.deepEqual(film.art, { id: "f", kind: "title" });
});

test("titles open the reader, watch view or notes; missing documents do not open", () => {
  assert.equal(openHref(tome(1, { id: "c" })), "/reader/c/");
  assert.equal(
    openHref(entry({ id: "v", videoAvailable: false, mediaAvailable: false })),
    "/player/v/",
  );
  assert.equal(openHref(entry({ documentAvailable: false })), null);
});
