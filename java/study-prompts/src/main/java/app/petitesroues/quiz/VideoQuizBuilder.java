package app.petitesroues.quiz;

import app.petitesroues.model.document.StudyDocument;
import java.util.List;

/**
 * Builds the self-contained quiz brief for a video, the one implementation every platform uses: the
 * quiz rules, the optional quiz type, then the subtitle lines of the chosen scope with their
 * translations. Performs no file access, persistence, sharing or model calls. Learner notes are
 * deliberately excluded. Deterministic: equal inputs give equal briefs.
 */
public final class VideoQuizBuilder {
  private VideoQuizBuilder() {}

  /** The Markdown the brief is built from: the quiz rules and the quiz types file. */
  public record Guides(String rules, String types) {
    public Guides {
      if (rules == null || types == null)
        throw new IllegalArgumentException("Quiz rules and types are required");
    }
  }

  /**
   * A prepared brief: its Markdown {@code text}, a suggested {@code fileName}, the number of
   * subtitle lines in it and whether that count is {@linkplain #large large}.
   */
  public record Brief(String text, String fileName, int lineCount, boolean large) {}

  /** The number of subtitle lines {@code scope} selects from {@code document}, a video. */
  public static int count(StudyDocument document, VideoQuizScope scope) {
    return scope.select(document.video()).size();
  }

  /** Whether {@code lineCount} lines are more than {@link VideoQuizScope#LARGE_LINES}. */
  public static boolean large(int lineCount) {
    return lineCount > VideoQuizScope.LARGE_LINES;
  }

  /**
   * Builds the brief for the lines {@code scope} selects from {@code document}, a video; an empty
   * selection still gives a brief that says so. {@code type} is optional ({@code null} for none)
   * and appears above the rules.
   *
   * @throws IllegalArgumentException if the document is a comic
   */
  public static Brief build(
      Guides guides, StudyDocument document, VideoQuizScope scope, QuizType type) {
    var selected = scope.select(document.video());
    var entries =
        selected.stream()
            .map(
                cue ->
                    new QuizBrief.Entry(
                        QuizTime.format(cue.start()),
                        cue.line().source(),
                        cue.line().translation()))
            .toList();
    String text =
        QuizBrief.render(
            "Subtitle quiz: " + document.title(),
            "Run the subtitle quiz below for a learner, using the selected subtitle lines. "
                + "Start the first exercise straight away. Instructions about the browser player "
                + "do not apply here, and the rules mention files from the library (a teaching "
                + "guide, documents) that are not included: work from the lines below.",
            document,
            type == null ? null : type.chosen(guides.types(), document),
            guides.rules(),
            scope.heading(selected),
            scope.emptyText(),
            List.of(new QuizBrief.Group(null, entries)));
    return new Brief(
        text,
        QuizBrief.fileName(document.title(), scope.fileNamePart()),
        selected.size(),
        large(selected.size()));
  }
}
