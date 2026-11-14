package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuizGuidesTest {
  @Test
  void bundledGuidesAreTheRepositoryFiles() throws IOException {
    var guides = QuizGuides.bundled();
    assertEquals(
        Files.readString(Path.of("../../agents/workflows/subtitle-quiz.md")), guides.rules());
    assertEquals(Files.readString(Path.of("../../agents/workflows/quiz-types.md")), guides.types());
    assertTrue(guides.rules().startsWith("# Subtitle quiz agent"));
    assertTrue(guides.types().contains("## Verb tenses"));
  }

  /**
   * The rules are pasted into every brief, so the scope section must stay short. Checked once the
   * section exists in subtitle-quiz.md.
   */
  @Test
  void theQuizScopeSectionStaysUnderTheBudget() {
    var sections = QuizGuides.bundled().rules().split("(?m)^## ");
    for (var section : sections) {
      if (section.startsWith("Quiz scope"))
        assertTrue(section.length() < 250, "Quiz scope section is " + section.length() + " chars");
    }
  }
}
