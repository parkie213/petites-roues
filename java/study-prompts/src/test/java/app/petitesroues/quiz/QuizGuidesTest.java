package app.petitesroues.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
   * The rules are pasted into every brief, so the scope section must stay short. Fails if the
   * section is missing, so the budget cannot pass vacuously.
   */
  @Test
  void theQuizScopeSectionStaysUnderTheBudget() {
    var sections = QuizGuides.bundled().rules().split("(?m)^## ");
    var scope = Arrays.stream(sections).filter(s -> s.startsWith("Quiz scope")).findFirst();
    assertTrue(scope.isPresent(), "subtitle-quiz.md has no Quiz scope section");
    assertTrue(
        scope.get().length() < 250, "Quiz scope section is " + scope.get().length() + " chars");
  }
}
