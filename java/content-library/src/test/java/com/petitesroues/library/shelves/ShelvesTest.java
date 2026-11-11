package com.petitesroues.library.shelves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.petitesroues.library.discovery.MediaKind;
import com.petitesroues.library.shelves.Shelves.Group;
import com.petitesroues.library.shelves.Shelves.Shelf;
import com.petitesroues.library.shelves.Shelves.Slot;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShelvesTest {
  /** A library entry; fields default to a video title whose document and media are present. */
  private static final class E implements Shelvable {
    private static int next;
    final String id = "title-" + ++next;
    MediaKind kind = MediaKind.VIDEO;
    String documentId = id, title = "Untitled", series, shelf, language, mediaPath;
    Integer season, episode, volume;
    boolean document = true, media = true, titleArt, seriesPoster;

    E kind(MediaKind value) {
      kind = value;
      return this;
    }

    E documentId(String value) {
      documentId = value;
      return this;
    }

    E title(String value) {
      title = value;
      return this;
    }

    E series(String value) {
      series = value;
      return this;
    }

    E shelf(String value) {
      shelf = value;
      return this;
    }

    E language(String value) {
      language = value;
      return this;
    }

    E mediaPath(String value) {
      mediaPath = value;
      return this;
    }

    E season(Integer value) {
      season = value;
      return this;
    }

    E episode(Integer value) {
      episode = value;
      return this;
    }

    E volume(Integer value) {
      volume = value;
      return this;
    }

    E media(boolean value) {
      media = value;
      return this;
    }

    E document(boolean value) {
      document = value;
      return this;
    }

    E titleArt() {
      titleArt = true;
      return this;
    }

    E seriesPoster() {
      seriesPoster = true;
      return this;
    }

    @Override
    public MediaKind kind() {
      return kind;
    }

    @Override
    public String documentId() {
      return documentId;
    }

    @Override
    public String title() {
      return title;
    }

    @Override
    public String series() {
      return series;
    }

    @Override
    public Integer season() {
      return season;
    }

    @Override
    public Integer episode() {
      return episode;
    }

    @Override
    public Integer volume() {
      return volume;
    }

    @Override
    public String shelf() {
      return shelf;
    }

    @Override
    public String sourceLanguage() {
      return language;
    }

    @Override
    public boolean documentAvailable() {
      return document;
    }

    @Override
    public boolean mediaAvailable() {
      return media;
    }

    @Override
    public boolean hasTitleArt() {
      return titleArt;
    }

    @Override
    public boolean hasSeriesPoster() {
      return seriesPoster;
    }

    @Override
    public String mediaPath() {
      return mediaPath;
    }

    @Override
    public String toString() {
      return id + " " + title;
    }
  }

  /**
   * Media no document describes, named from its path as the platforms do: no language, its own
   * document ID, and a comic's first page as its art.
   */
  private static E bare(String path, MediaKind kind) {
    var named = MediaNames.fromPath(path);
    var entry =
        new E()
            .kind(kind)
            .documentId("media:" + path)
            .title(named.title())
            .series(named.series())
            .season(named.season())
            .episode(named.episode())
            .document(false)
            .mediaPath(path);
    if (kind == MediaKind.COMIC) entry.titleArt();
    return entry;
  }

  private static E tome(int volume) {
    return new E()
        .kind(MediaKind.COMIC)
        .series("Demon Slayer")
        .title("Demon Slayer — Tome " + volume)
        .volume(volume);
  }

  private static E episode(int season, int number) {
    return new E().series("Peppa Pig").title("Episode " + number).season(season).episode(number);
  }

  private static List<Group<E>> shelf(List<Shelf<E>> shelves, String key) {
    return shelves.stream()
        .filter(s -> s.key().equals(key))
        .findFirst()
        .map(Shelf::groups)
        .orElse(List.of());
  }

  private static List<Group<E>> shelf(String key, E... entries) {
    return shelf(Shelves.shelves(Arrays.asList(entries)), key);
  }

  private static List<String> labels(Group<E> group) {
    return group.slots().stream().map(Slot::label).toList();
  }

  @Test
  void mangaAndAnimeOfOneSeriesAreSeparateCardsAndASeriesFilmIsAFilm() {
    var result =
        Shelves.shelves(
            List.of(
                tome(2),
                tome(1),
                tome(10),
                episode(1, 1).series("Demon Slayer"),
                new E().series("Demon Slayer").title("Mugen Train"),
                new E().title("Princesse Mononoké")));
    var manga = shelf(result, "comics").get(0);
    assertEquals("Demon Slayer", manga.name());
    assertEquals("3 tomes", manga.count());
    assertEquals(List.of("Tome 1", "Tome 2", "Tome 10"), labels(manga));
    var anime = shelf(result, "series").get(0);
    assertEquals("Demon Slayer", anime.name());
    assertEquals("1 episode", anime.count());
    assertEquals(
        List.of("Mugen Train", "Princesse Mononoké"),
        shelf(result, "films").stream().map(Group::name).toList());
    assertTrue(shelf(result, "films").stream().allMatch(Group::single));
  }

  @Test
  void episodesSortBySeasonThenEpisodeAndSeasonsAreCountedOnlyWhenSeveral() {
    var peppa = shelf("series", episode(1, 2), episode(1, 10), episode(1, 1)).get(0);
    assertEquals("3 episodes", peppa.count());
    assertEquals(
        List.of("S1 · E1", "S1 · E2", "S1 · E10"),
        peppa.slots().stream().map(Slot::detail).toList());
    peppa = shelf("series", episode(2, 1), episode(1, 1), episode(1, 2)).get(0);
    assertEquals(List.of(1, 2), peppa.seasons());
    assertEquals("2 seasons · 3 episodes", peppa.count());
    assertEquals(
        List.of("S1 · E1", "S1 · E2", "S2 · E1"),
        peppa.slots().stream().map(Slot::detail).toList());
  }

  @Test
  void aSeriesWithoutNumbersSuchAsAChannelStaysASeriesOfVideos() {
    var parisien =
        shelf(
                "series",
                new E().series("Le Parisien").title("Paris : cette boulangerie"),
                new E().series("Le Parisien").title("Des frigos piratés"))
            .get(0);
    assertEquals("2 videos", parisien.count());
    assertEquals(List.of("Des frigos piratés", "Paris : cette boulangerie"), labels(parisien));
  }

  @Test
  void twoFoldersMergeIntoOneSeriesAndTheSameEpisodeBecomesOneSlotWithCopies() {
    var missing = episode(1, 1).media(false);
    var present = episode(1, 1);
    var groups =
        shelf(
            "series",
            missing,
            episode(1, 2).series("Peppa  pig"),
            present,
            episode(2, 1).series("peppa pig "));
    assertEquals(1, groups.size());
    var peppa = groups.get(0);
    assertEquals("Peppa Pig", peppa.name(), "the most common spelling is shown");
    assertEquals(3, peppa.slots().size());
    var first = peppa.slots().get(0);
    assertEquals(List.of(present, missing), first.copies());
    assertSame(present, first.entry(), "the copy with media is preferred");
  }

  @Test
  void equallyAvailableCopiesKeepFolderOrder() {
    var a = new E().documentId("robots").title("ROBOTS");
    var b = new E().documentId("robots").title("ROBOTS");
    var film = shelf("films", a, b).get(0);
    assertEquals(2, film.slots().get(0).copies().size());
    assertSame(a, film.slots().get(0).entry());
  }

  @Test
  void cardArtIsTheSeriesPosterElseTheFirstSlotWithArtElseNone() {
    var first = tome(1).titleArt();
    var manga = shelf("comics", tome(2), first).get(0);
    assertEquals(new Shelves.CardArt<>(first, false), manga.art());
    assertEquals(new Shelves.CardArt<>(first, false), manga.slots().get(0).art());

    var second = tome(2).seriesPoster();
    manga = shelf("comics", tome(1), second).get(0);
    assertEquals(new Shelves.CardArt<>(second, true), manga.art());

    var peppa = shelf("series", episode(1, 1), episode(1, 2)).get(0);
    assertNull(peppa.art(), "videos have no art without a poster");
    var film = new E().title("Film").titleArt();
    assertEquals(new Shelves.CardArt<>(film, false), shelf("films", film).get(0).art());
  }

  @Test
  void mediaWithoutADocumentJoinsItsSeriesBesideTitlesWithDocuments() {
    var linked = new E().series("Peppa Pig").season(1).episode(2).title("Mr Dinosaure");
    var media = bare("peppa-pig/s01e01-les-flaques-de-boue.mp4", MediaKind.VIDEO).seriesPoster();
    var group = shelf("series", linked, media).get(0);
    assertEquals("Peppa Pig", group.name());
    assertEquals(List.of(media, linked), group.slots().stream().map(Slot::entry).toList());
    assertEquals(new Shelves.CardArt<>(media, true), group.art());
  }

  @Test
  void aBareFilmUsesItsFolderPosterAndABareComicItsOwnCard() {
    var film = bare("Ponyo.mkv", MediaKind.VIDEO).seriesPoster();
    assertEquals(new Shelves.CardArt<>(film, true), shelf("unsorted", film).get(0).art());
    var comic = bare("volume.cbz", MediaKind.COMIC);
    assertEquals(new Shelves.CardArt<>(comic, false), shelf("comics", comic).get(0).art());
  }

  @Test
  void videosNothingDescribesAreUnsortedEachOnItsOwn() {
    var shrek = bare("films/shrek/shrek.mkv", MediaKind.VIDEO);
    var shrek2 = bare("films/shrek/shrek-2.mkv", MediaKind.VIDEO);
    var episode = bare("peppa-pig/s01e01-les-flaques-de-boue.mp4", MediaKind.VIDEO);
    var channel = new E().series("Le Parisien").title("Documented");
    var clip = bare("le-parisien/clip.mp4", MediaKind.VIDEO);
    var unsorted = shelf("unsorted", shrek, shrek2, episode, channel, clip);
    assertEquals(
        List.of("Shrek", "Shrek 2"),
        unsorted.stream().map(Shelves.Group::name).toList(),
        "a folder name alone does not make a series");
    assertEquals(1, shelf("series", shrek, episode).size(), "an episode number does");
    assertEquals(
        2,
        shelf("series", channel, clip).get(0).slots().size(),
        "so does a series a documented video belongs to");
  }

  @Test
  void aDocumentCanNameItsShelfAndASeriesSitsWhereMostOfItsTitlesSay() {
    var result =
        Shelves.shelves(
            List.of(
                tome(1).shelf("Manga"),
                tome(2).shelf("Manga"),
                tome(3),
                new E().kind(MediaKind.COMIC).title("Lulu"),
                new E().title("Ponyo").shelf(" Ghibli ")));
    assertEquals(
        List.of(
            List.of("Comics", "Lulu"),
            List.of("Ghibli", "Ponyo"),
            List.of("Manga", "Demon Slayer")),
        result.stream().map(s -> List.of(s.label(), s.groups().get(0).name())).toList());
    var series = shelf(result, "manga").get(0);
    assertEquals("3 tomes", series.count(), "the unshelved tome stays with its series");
    assertEquals("manga:demon slayer", series.key());
    assertEquals("manga", series.shelf());
    assertEquals(MediaKind.COMIC, series.kind());
  }

  @Test
  void aSeriesInTwoLanguagesIsOneCardPerLanguage() {
    var groups =
        shelf(
            "series",
            episode(1, 1).language("fr"),
            episode(1, 2).language("fr"),
            episode(1, 1).language("zh"));
    assertEquals(
        List.of(List.of("fr", 2), List.of("zh", 1)),
        groups.stream().map(g -> List.<Object>of(g.language(), g.slots().size())).toList());
    assertFalse(groups.get(0).key().equals(groups.get(1).key()));
  }

  @Test
  void mediaWithoutADocumentJoinsItsSeriesOnlyLanguage() {
    var linked = episode(1, 34).language("zh");
    var media = bare("series/peppa-pig/s01e35-other.mp4", MediaKind.VIDEO);
    var group = shelf("series", linked, media).get(0);
    assertEquals("zh", group.language());
    assertEquals(2, group.slots().size());
    var french = episode(1, 1).language("fr");
    assertEquals(3, shelf("series", linked, french, media).size());
  }

  @Test
  void aComicWhoseDocumentNamesNoSeriesJoinsTheSeriesItsFolderNames() {
    var media = bare("comics/naruto/Naruto-T02.cbz", MediaKind.COMIC);
    var documented =
        new E()
            .kind(MediaKind.COMIC)
            .title("Naruto T01")
            .language("fr")
            .mediaPath("comics/naruto/Naruto-T01.cbz");
    var cards = shelf("comics", documented, media);
    assertEquals(1, cards.size());
    assertEquals("Naruto", cards.get(0).name());
    assertEquals(List.of("Naruto T01", "Naruto T02"), labels(cards.get(0)));
  }

  @Test
  void numbersCompareByValue() {
    assertTrue(Shelves.NATURAL.compare("Tome 2", "Tome 10") < 0);
    assertTrue(Shelves.NATURAL.compare("Naruto T09", "Naruto T10") < 0);
    assertTrue(Shelves.NATURAL.compare("des frigos", "Paris") < 0);
  }
}
