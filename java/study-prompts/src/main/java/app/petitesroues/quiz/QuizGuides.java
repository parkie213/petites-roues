package app.petitesroues.quiz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the quiz rules and quiz types packaged in this jar from the repository's
 * agents/workflows/subtitle-quiz.md and quiz-types.md (see this module's pom), so a packaged server
 * or app needs no checkout. The files are read from the classpath on each call; callers keep the
 * result.
 */
public final class QuizGuides {
  private QuizGuides() {}

  /**
   * The bundled rules and types.
   *
   * @throws IllegalStateException if the jar was built without them
   * @throws UncheckedIOException if reading fails
   */
  public static VideoQuizBuilder.Guides bundled() {
    return new VideoQuizBuilder.Guides(read("subtitle-quiz.md"), read("quiz-types.md"));
  }

  private static String read(String name) {
    try (InputStream in = QuizGuides.class.getResourceAsStream(name)) {
      if (in == null) throw new IllegalStateException("Quiz guide " + name + " is not bundled");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
