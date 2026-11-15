package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Holds the Java video quiz brief to the briefs the browser's original JavaScript builder produced
 * for the two example documents (the goldens), byte for byte. Exactly one difference is deliberate
 * and stated here: the whole-title section is headed "Whole title", where the old browser brief
 * said "Whole episode". The introduction sentence is identical to the old browser brief's (the
 * desktop wording), so it needs no exception.
 */
class VideoQuizBuilderGoldenTest {
  private static final String OLD_WHOLE_HEADING = "## Whole episode\n";
  private static final String NEW_WHOLE_HEADING = "## Whole title\n";

  private static String golden(String name) throws IOException {
    try (var in = VideoQuizBuilderGoldenTest.class.getResourceAsStream("/golden/" + name + ".md")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "peppa,example/documents-fr/series/peppa-pig/s01e01",
    "masha,example/documents-de/series/masha-and-the-bear/083"
  })
  void javaBriefEqualsTheOldBrowserBriefExceptTheWholeTitleHeading(String name, String directory)
      throws IOException {
    var document =
        new StudyDocumentReader(new StudyDocumentCodec())
            .read(Path.of("../..", directory, "document.json"));
    var guides = QuizGuides.bundled();
    var whole = new VideoQuizScope.Whole();
    var range = new VideoQuizScope.TimeRange(10, 40);

    assertEquals(
        golden(name + "-whole").replace(OLD_WHOLE_HEADING, NEW_WHOLE_HEADING),
        VideoQuizBuilder.build(guides, document, whole, null).text());
    assertEquals(
        golden(name + "-whole-tenses").replace(OLD_WHOLE_HEADING, NEW_WHOLE_HEADING),
        VideoQuizBuilder.build(guides, document, whole, QuizType.TENSES).text());
    assertEquals(
        golden(name + "-range"), VideoQuizBuilder.build(guides, document, range, null).text());
    assertEquals(
        golden(name + "-range-tenses"),
        VideoQuizBuilder.build(guides, document, range, QuizType.TENSES).text());
  }
}
