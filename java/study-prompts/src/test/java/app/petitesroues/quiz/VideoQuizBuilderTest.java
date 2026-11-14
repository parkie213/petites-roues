package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.document.VideoDocument;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VideoQuizBuilderTest {
  private static final VideoQuizBuilder.Guides GUIDES =
      new VideoQuizBuilder.Guides("# Subtitle quiz agent\n\nRules.", "# Quiz types");

  private static VideoCue at(double start, String source) {
    return new VideoCue(Line.of("c" + start, source), start, start + 2, null);
  }

  private static StudyDocument document(String title, List<VideoCue> cues) {
    return new StudyDocument(
        "id", title, "fr", "en", "en", null, null, new VideoDocument(null, null, null, cues));
  }

  private static StudyDocument document(List<VideoCue> cues) {
    return document("Le Film", cues);
  }

  private static String build(List<VideoCue> cues, VideoQuizScope scope) {
    return VideoQuizBuilder.build(GUIDES, document(cues), scope, null).text();
  }

  @Test
  void carriesTheRulesAndLinesWithTheirTranslationsButNotNotes() {
    var withNotes =
        new VideoCue(
            Line.of("a", "Il fait beau")
                .withTranslation("The weather is nice")
                .withNotes(List.of("Weather uses faire.")),
            10.0,
            12.0,
            null);
    var text =
        build(List.of(withNotes, at(70, "Pas encore vu")), new VideoQuizScope.TimeRange(0, 30));
    assertTrue(text.startsWith("# Subtitle quiz: Le Film\n"));
    assertTrue(text.contains("### Subtitle quiz agent"), "bundled headings are demoted");
    assertFalse(text.contains("Teaching guide"));
    assertTrue(text.contains("`fr`"));
    assertTrue(text.contains("\n## Lines in time range (0:00–0:30)\n"));
    assertTrue(text.contains("\n- 0:10 Il fait beau → The weather is nice\n"));
    assertFalse(text.contains("Weather uses faire"), "learner notes stay out");
    assertFalse(text.contains("Pas encore vu"), "lines outside the range stay out");
  }

  @Test
  void wholeTitleIncludesEverySourceLineWithoutALimit() {
    var cues = new ArrayList<VideoCue>();
    for (int i = 0; i < 45; i++) cues.add(at(i * 3, "Line " + i));
    cues.add(at(150, " "));
    var text = build(cues, new VideoQuizScope.Whole());
    assertEquals(45, text.lines().filter(line -> line.matches("- \\d+:\\d\\d .*")).count());
    assertTrue(text.contains("\n## Whole title\n"));
    assertFalse(text.contains("Whole episode"));
  }

  @Test
  void rangeIncludesOverlapsAndExcludesTouchingBoundaries() {
    var text =
        build(
            List.of(
                at(8, "Before"),
                at(9, "Overlaps start"),
                at(15, "Inside"),
                at(19, "Overlaps end"),
                at(20, "After")),
            new VideoQuizScope.TimeRange(10, 20));
    for (String kept : new String[] {"Overlaps start", "Inside", "Overlaps end"})
      assertTrue(text.contains(kept), kept);
    for (String dropped : new String[] {"Before", "After"})
      assertFalse(text.contains(dropped), dropped);
  }

  @Test
  void blankSourceAndLinelessCuesAreSkipped() {
    var text =
        build(
            List.of(at(1, "  "), new VideoCue(null, 2.0, 4.0, "check"), at(5, "Kept")),
            new VideoQuizScope.Whole());
    assertEquals(1, text.lines().filter(line -> line.startsWith("- 0:")).count());
    assertTrue(text.contains("- 0:05 Kept\n"));
  }

  @Test
  void linesAreListedInPlaybackOrder() {
    var text = build(List.of(at(30, "Second"), at(5, "First")), new VideoQuizScope.Whole());
    assertTrue(text.indexOf("First") < text.indexOf("Second"));
  }

  @Test
  void emptySelectionsSayNoLinesAndSkipTheSpreadInstruction() {
    var range = build(List.of(), new VideoQuizScope.TimeRange(0, 10));
    assertTrue(range.endsWith("No subtitle lines in this selection.\n"));
    assertFalse(range.contains("Pick items from all over"));
    assertTrue(build(List.of(), new VideoQuizScope.Whole()).contains("No subtitle lines"));
  }

  @Test
  void nonEmptySelectionsAskForItemsFromAllOver() {
    assertTrue(
        build(List.of(at(1, "Salut")), new VideoQuizScope.Whole())
            .contains("Pick items from all over these lines, in a mixed order"));
  }

  @Test
  void theChosenTypeSitsAboveTheRules() {
    var types = "# Quiz types\n\n## Verb tenses\n\nAsk for the tense or mood and the infinitive.\n";
    var guides = new VideoQuizBuilder.Guides("# Rules", types);
    var text =
        VideoQuizBuilder.build(
                guides,
                document(List.of(at(1, "Il est parti"))),
                new VideoQuizScope.Whole(),
                QuizType.TENSES)
            .text();
    int type = text.indexOf("## Quiz type: Verb tenses");
    assertTrue(type > 0 && type < text.indexOf("## Quiz rules"));
    assertTrue(text.contains("Ask for the tense or mood and the infinitive"));
    assertFalse(build(List.of(at(1, "Salut")), new VideoQuizScope.Whole()).contains("Quiz type"));
  }

  @Test
  void reportsLineCountAndLargeFlag() {
    var cues = new ArrayList<VideoCue>();
    for (int i = 0; i < 401; i++) cues.add(at(i, "Line " + i));
    var document = document(cues);
    var all = VideoQuizBuilder.build(GUIDES, document, new VideoQuizScope.Whole(), null);
    assertEquals(401, all.lineCount());
    assertTrue(all.large());
    var some = VideoQuizBuilder.build(GUIDES, document, new VideoQuizScope.TimeRange(0, 10), null);
    assertEquals(
        VideoQuizBuilder.count(document, new VideoQuizScope.TimeRange(0, 10)), some.lineCount());
    assertFalse(some.large());
  }

  @Test
  void namesTheFileAfterTheTitleAndRange() {
    var cues = List.of(at(1, "Salut"));
    assertEquals(
        "quiz-le-château-ambulant.md",
        VideoQuizBuilder.build(
                GUIDES, document("Le Château ambulant !", cues), new VideoQuizScope.Whole(), null)
            .fileName());
    assertEquals(
        "quiz-le-film-12-05-to-22-05.md",
        VideoQuizBuilder.build(
                GUIDES, document(cues), new VideoQuizScope.TimeRange(725, 1325), null)
            .fileName());
    assertEquals(
        "quiz-title.md",
        VideoQuizBuilder.build(GUIDES, document("?!", cues), new VideoQuizScope.Whole(), null)
            .fileName());
  }

  @Test
  void rejectsMissingGuidesAndComics() {
    assertThrows(IllegalArgumentException.class, () -> new VideoQuizBuilder.Guides(null, "x"));
    assertThrows(IllegalArgumentException.class, () -> new VideoQuizBuilder.Guides("x", null));
    var comic =
        new StudyDocument(
            "id",
            "Comic",
            "fr",
            "en",
            "en",
            null,
            null,
            new ComicDocument(null, null, null, List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> VideoQuizBuilder.build(GUIDES, comic, new VideoQuizScope.Whole(), null));
  }
}
