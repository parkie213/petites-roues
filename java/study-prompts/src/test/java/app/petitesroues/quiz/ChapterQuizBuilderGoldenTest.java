package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.documents.StudyDocumentReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Holds the chapter quiz brief byte for byte to briefs recorded before its Markdown skeleton was
 * shared with the video quiz, so extracting that skeleton cannot change comic output.
 */
class ChapterQuizBuilderGoldenTest {
  private static final Path EXAMPLE =
      Path.of("../../example/documents-fr/comics/lulu/le-parapluie-de-lulu/document.json");

  private static String golden(String name) throws IOException {
    try (var in = ChapterQuizBuilderGoldenTest.class.getResourceAsStream("/golden/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String chapterBrief(ChapterQuizBuilder.Type type) throws IOException {
    var document = new StudyDocumentReader(new StudyDocumentCodec()).read(EXAMPLE);
    var rules = Files.readString(Path.of("../../agents/workflows/subtitle-quiz.md"));
    return ChapterQuizBuilder.build(rules, document, 0, type).text();
  }

  @Test
  void chapterBriefWithoutTypeIsUnchanged() throws IOException {
    assertEquals(golden("lulu-chapter.md"), chapterBrief(null));
  }

  @Test
  void chapterBriefWithTypeIsUnchanged() throws IOException {
    var type = new ChapterQuizBuilder.Type("Vocabulary", "Ask about words.\n\nOne per turn.");
    assertEquals(golden("lulu-chapter-vocabulary.md"), chapterBrief(type));
  }
}
