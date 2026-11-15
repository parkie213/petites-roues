package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.document.VideoDocument;
import app.petitesroues.model.document.VideoReference;
import app.petitesroues.quiz.VideoQuizScope.Form;
import app.petitesroues.quiz.VideoQuizScope.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VideoQuizScopeTest {
  private static VideoCue cue(double start, double end) {
    return new VideoCue(Line.of("c" + start, "Line"), start, end, null);
  }

  private static StudyDocument document(String shelf, Double duration, List<VideoCue> cues) {
    var reference = duration == null ? null : new VideoReference(null, null, duration);
    return new StudyDocument(
        "id",
        "T",
        "fr",
        "en",
        "en",
        null,
        shelf,
        null,
        new VideoDocument(null, null, reference, cues));
  }

  @Test
  void shortTitlesAndNonFilmShelvesOpenOnTheWholeTitle() {
    assertEquals(
        Kind.WHOLE, VideoQuizScope.defaultFor(document(null, 899.0, List.of()), 300, null).kind());
    assertEquals(
        Kind.RANGE, VideoQuizScope.defaultFor(document(null, 900.0, List.of()), 300, null).kind());
    assertEquals(
        Kind.WHOLE,
        VideoQuizScope.defaultFor(document("Series", 5400.0, List.of()), 300, null).kind());
    assertEquals(
        Kind.WHOLE,
        VideoQuizScope.defaultFor(document("Manga", 5400.0, List.of()), 300, null).kind());
    assertEquals(
        Kind.RANGE,
        VideoQuizScope.defaultFor(document("films", 5400.0, List.of()), 300, null).kind());
    assertEquals(
        Kind.RANGE,
        VideoQuizScope.defaultFor(document("Films", 5400.0, List.of()), 300, null).kind());
  }

  @Test
  void rangeFieldsAreAlwaysPrefilledFromThePlayhead() {
    var film = document(null, 5400.0, List.of());
    assertEquals(
        new Form(Kind.RANGE, "12:05", "22:05"), VideoQuizScope.defaultFor(film, 1325.2, null));
    assertEquals(new Form(Kind.RANGE, "0:00", "5:00"), VideoQuizScope.defaultFor(film, 300, null));
    var shortTitle = document(null, 60.0, List.of());
    assertEquals(
        new Form(Kind.WHOLE, "0:00", "1:00"), VideoQuizScope.defaultFor(shortTitle, 0, null));
  }

  @Test
  void beforePlaybackBeginsTheRangeIsTheFirstTenMinutes() {
    var film = document(null, 5400.0, List.of());
    assertEquals(new Form(Kind.RANGE, "0:00", "10:00"), VideoQuizScope.defaultFor(film, 0.4, null));
  }

  @Test
  void lengthFallsBackFromReferenceToPlayerToLastCue() {
    var cues = List.of(cue(1, 3), cue(100, 130.5), cue(50, 52));
    assertEquals(5400, VideoQuizScope.length(document(null, 5400.0, cues), 77.0));
    assertEquals(77, VideoQuizScope.length(document(null, null, cues), 77.0));
    assertEquals(130.5, VideoQuizScope.length(document(null, null, cues), null));
    assertEquals(130.5, VideoQuizScope.length(document(null, 0.0, cues), Double.NaN));
    assertEquals(0, VideoQuizScope.length(document(null, null, List.of()), null));
  }

  @Test
  void aDocumentWithoutAReferenceUsesThePlayerDurationForTheDefault() {
    var film = document(null, null, List.of(cue(1, 3)));
    assertEquals(Kind.RANGE, VideoQuizScope.defaultFor(film, 2000, 5400.0).kind());
    assertEquals(Kind.WHOLE, VideoQuizScope.defaultFor(film, 20, null).kind());
  }

  @Test
  void largeStartsAboveFourHundredLines() {
    var cues400 = new ArrayList<VideoCue>();
    for (int i = 0; i < 400; i++) cues400.add(cue(i, i + 1));
    var cues401 = new ArrayList<>(cues400);
    cues401.add(cue(400, 401));
    var whole = new VideoQuizScope.Whole();
    assertEquals(400, VideoQuizBuilder.count(document(null, null, cues400), whole));
    assertFalse(VideoQuizBuilder.large(400));
    assertEquals(401, VideoQuizBuilder.count(document(null, null, cues401), whole));
    assertTrue(VideoQuizBuilder.large(401));
  }

  @Test
  void ofReadsTheFormOrIsEmptyWhileTheRangeIsUnreadable() {
    assertEquals(
        new VideoQuizScope.Whole(),
        VideoQuizScope.of(new Form(Kind.WHOLE, "junk", "")).orElseThrow());
    assertEquals(
        new VideoQuizScope.TimeRange(725, 1325),
        VideoQuizScope.of(new Form(Kind.RANGE, "12:05", "22:05")).orElseThrow());
    assertEquals(
        new VideoQuizScope.TimeRange(3600, 3723),
        VideoQuizScope.of(new Form(Kind.RANGE, "1:00:00", "1:02:03")).orElseThrow());
    for (var bad :
        List.of(
            new Form(Kind.RANGE, "12:05", "12:05"),
            new Form(Kind.RANGE, "22:05", "12:05"),
            new Form(Kind.RANGE, "12", "22:05"),
            new Form(Kind.RANGE, "12:05", ""),
            new Form(Kind.RANGE, null, null),
            new Form(Kind.RANGE, "1:60", "2:00"),
            new Form(Kind.RANGE, "5.5", "9.5")))
      assertTrue(VideoQuizScope.of(bad).isEmpty(), bad.toString());
  }

  @Test
  void timeRangesMustBeFiniteAndOrdered() {
    assertThrows(IllegalArgumentException.class, () -> new VideoQuizScope.TimeRange(-1, 5));
    assertThrows(IllegalArgumentException.class, () -> new VideoQuizScope.TimeRange(5, 5));
    assertThrows(IllegalArgumentException.class, () -> new VideoQuizScope.TimeRange(5, 4));
    assertThrows(IllegalArgumentException.class, () -> new VideoQuizScope.TimeRange(0, Double.NaN));
    assertThrows(
        IllegalArgumentException.class,
        () -> new VideoQuizScope.TimeRange(0, Double.POSITIVE_INFINITY));
  }

  @Test
  void headingsAndFileNameParts() {
    var whole = new VideoQuizScope.Whole();
    var range = new VideoQuizScope.TimeRange(725, 3725);
    assertEquals("Whole title", whole.heading(List.of()));
    assertEquals("Lines in time range (12:05–1:02:05)", range.heading(List.of()));
    assertEquals("", whole.fileNamePart());
    assertEquals("12:05 to 1:02:05", range.fileNamePart());
    assertEquals("No subtitle lines in this selection.", whole.emptyText());
  }

  @Test
  void selectUsesAHalfOpenOverlapInPlaybackOrder() {
    var video =
        document(null, null, List.of(cue(20, 22), cue(8, 10), cue(9, 11), cue(19, 20))).video();
    var picked = new VideoQuizScope.TimeRange(10, 20).select(video);
    assertEquals(List.of(9.0, 19.0), picked.stream().map(VideoCue::start).toList());
  }
}
