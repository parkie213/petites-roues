package app.petitesroues.quiz;

import static app.petitesroues.support.DocumentFixtures.comic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.petitesroues.model.document.ComicPage;
import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.document.VideoDocument;
import app.petitesroues.model.editing.VideoCueEdit;
import app.petitesroues.support.HttpIntegrationTest;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The video quiz route prepares from the served document and never writes anything. */
class VideoQuizIntegrationTest extends HttpIntegrationTest {
  private static final String QUIZ = "/api/titles/film/quiz";

  private static VideoCue cue(String id, double start, String source, String translation) {
    return new VideoCue(Line.of(id, source).withTranslation(translation), start, start + 2, null);
  }

  private StudyDocument registerFilm() throws Exception {
    var film =
        new StudyDocument(
            "film",
            "Le Film",
            "fr",
            "en",
            "en",
            null,
            null,
            new VideoDocument(
                null,
                null,
                null,
                List.of(
                    cue("early", 100, "Tôt", "Early"),
                    cue("middle", 800, "Au milieu", "In the middle"),
                    cue("late", 1400, "Tard", "Late"))));
    registerTitle(film);
    return film;
  }

  private JsonNode get(String query) {
    return http.get().uri(QUIZ + query).retrieve().body(JsonNode.class);
  }

  private int status(String uri) {
    return http.get()
        .uri(uri)
        .<Integer>exchange((request, response) -> response.getStatusCode().value());
  }

  @Test
  void withoutAScopeTheDefaultRuleAppliesAndTheReplyCarriesTheWholeMenu() throws Exception {
    registerFilm();
    var long_ = get("?position=1325.2&duration=5400");
    assertEquals("range", long_.get("scope").get("kind").asString());
    assertEquals("12:05", long_.get("scope").get("start").asString());
    assertEquals("22:05", long_.get("scope").get("end").asString());
    assertTrue(long_.get("valid").asBoolean());
    assertEquals(1, long_.get("lineCount").asInt());
    assertFalse(long_.get("large").asBoolean());
    assertEquals(400, long_.get("largeAbove").asInt());
    assertEquals("to-source", long_.get("type").asString());
    assertEquals(6, long_.get("types").size());
    assertEquals("to-source", long_.get("types").get(0).get("id").asString());
    assertEquals("Translate into French", long_.get("types").get(0).get("label").asString());
    assertEquals("Translate into English", long_.get("types").get(1).get("label").asString());
    assertEquals("quiz-le-film-12-05-to-22-05.md", long_.get("fileName").asString());
    var text = long_.get("text").asString();
    assertTrue(text.startsWith("# Subtitle quiz: Le Film\n"));
    assertTrue(text.contains("## Lines in time range (12:05–22:05)"));
    assertTrue(text.contains("- 13:20 Au milieu → In the middle\n"));
    assertTrue(text.contains("## Quiz type: Translate into French"));
    assertFalse(text.contains("Tôt") || text.contains("Tard"));

    var shortTitle = get("?position=0&duration=60");
    assertEquals("whole", shortTitle.get("scope").get("kind").asString());
    assertEquals(3, shortTitle.get("lineCount").asInt());
    assertEquals("quiz-le-film.md", shortTitle.get("fileName").asString());
    assertTrue(shortTitle.get("text").asString().contains("## Whole title\n"));
  }

  @Test
  void anExplicitScopeAndTypeOverrideTheDefault() throws Exception {
    registerFilm();
    var whole = get("?scope=whole&type=tenses&position=0&duration=5400");
    assertEquals("whole", whole.get("scope").get("kind").asString());
    assertEquals("tenses", whole.get("type").asString());
    assertEquals(3, whole.get("lineCount").asInt());
    assertTrue(whole.get("text").asString().contains("## Quiz type: Verb tenses"));

    var range = get("?scope=range&start=1:00&end=3:00&position=0&duration=5400");
    assertEquals("1:00", range.get("scope").get("start").asString());
    assertEquals(1, range.get("lineCount").asInt());
    assertEquals("quiz-le-film-1-00-to-3-00.md", range.get("fileName").asString());
  }

  @Test
  void queuedEditsAppearInTheBriefButTheDocumentFileAndProgressAreUntouched() throws Exception {
    var film = registerFilm();
    var original = Files.readString(documentPath("film"));
    var middle = film.video().cues().get(1);
    http.put()
        .uri("/api/titles/film/cues/middle")
        .body(
            new VideoCueEdit(
                middle.start(),
                middle.end(),
                "Au centre",
                "In the centre",
                List.of(),
                false,
                null,
                null))
        .retrieve()
        .toBodilessEntity();
    var reply = get("?scope=whole&position=0&duration=60");
    assertTrue(reply.get("text").asString().contains("- 13:20 Au centre → In the centre\n"));
    assertFalse(reply.get("text").asString().contains("Au milieu"));
    assertEquals(original, Files.readString(documentPath("film")));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM playback_progress", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reading_progress", Integer.class));
  }

  @Test
  void anUnreadableRangeIsAValidFalseReplyNotAnError() throws Exception {
    registerFilm();
    for (String range : List.of("start=abc&end=2:00", "start=2:00&end=1:00", "start=90&end=2:00")) {
      var reply = get("?scope=range&" + range + "&position=0&duration=5400");
      assertFalse(reply.get("valid").asBoolean(), range);
      assertEquals(0, reply.get("lineCount").asInt());
      assertFalse(reply.get("large").asBoolean());
      assertTrue(reply.get("text").isNull());
      assertTrue(reply.get("fileName").isNull());
      assertEquals(6, reply.get("types").size());
    }
  }

  @Test
  void repliesAreNeverCached() throws Exception {
    registerFilm();
    var response = http.get().uri(QUIZ + "?position=0").retrieve().toEntity(JsonNode.class);
    assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
  }

  @Test
  void comicsAreBadRequestsAndUnknownTitlesAreNotFound() throws Exception {
    var page = new ComicPage("page", "001.jpg", null, List.of());
    registerTitle(comic("manga", "A Comic", ReadingDirection.LTR, List.of(page)));
    assertEquals(400, status("/api/titles/manga/quiz?position=0"));
    assertEquals(404, status("/api/titles/missing/quiz?position=0"));
    registerFilm();
    assertEquals(400, status(QUIZ + "?scope=last10&position=0"));
    assertEquals(400, status(QUIZ));
  }
}
