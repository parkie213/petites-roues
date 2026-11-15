package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizTypeTest {
  private static StudyDocument document(String source, String translation) {
    return new StudyDocument(
        "id",
        "Le Film",
        source,
        translation,
        "en",
        null,
        null,
        new VideoDocument(null, null, null, List.of()));
  }

  @Test
  void everyTypeHasInstructionsInTheBundledTypesFile() {
    var guides = QuizGuides.bundled();
    var document = document("fr", "en");
    for (var type : QuizType.values()) {
      var chosen = type.chosen(guides.types(), document);
      assertTrue(chosen.instructions().length() > 40, type.id());
      assertFalse(chosen.instructions().contains("## "), type.id() + " stops at the next type");
    }
  }

  @Test
  void labelsNameTheDocumentLanguages() {
    var french = document("fr", "en");
    assertEquals("Translate into French", QuizType.TO_SOURCE.label(french));
    assertEquals("Translate into English", QuizType.TO_TRANSLATION.label(french));
    assertEquals("Translate into German", QuizType.TO_SOURCE.label(document("de", "en")));
    assertEquals("Verb tenses", QuizType.TENSES.label(french));
    assertEquals("Mixed", QuizType.MIXED.label(french));
  }

  @Test
  void unusualLanguageTagsStillGiveALabel() {
    assertEquals("Translate into Unknown language", QuizType.TO_SOURCE.label(document("", "en")));
    assertEquals("Translate into xx", QuizType.TO_SOURCE.label(document("xx", "en")));
  }

  @Test
  void unknownIdsFallBackToTheFirstType() {
    assertEquals(QuizType.TO_SOURCE, QuizType.of("unknown"));
    assertEquals(QuizType.TO_SOURCE, QuizType.of(null));
    for (var type : QuizType.values()) assertEquals(type, QuizType.of(type.id()));
  }

  @Test
  void aTypeMissingFromTheFileGetsEmptyInstructions() {
    var chosen =
        QuizType.TENSES.chosen("# Quiz types\n\n## Mixed\n\nMix them up.\n", document("fr", "en"));
    assertEquals("Verb tenses", chosen.label());
    assertEquals("", chosen.instructions());
  }
}
