package app.petitesroues.quiz;

import app.petitesroues.library.LibraryService;
import app.petitesroues.quiz.VideoQuizScope.Form;
import app.petitesroues.quiz.VideoQuizScope.Kind;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Prepares a video quiz from the current served document, including queued learner edits: it
 * resolves the scope (the default rule when none is asked for), the quiz type and the time range,
 * and builds the brief with the bundled quiz guides. Performs no writes, so documents and progress
 * are untouched, and calls no model.
 */
@Service
public class VideoQuizService {
  private final LibraryService library;
  private final VideoQuizBuilder.Guides guides = QuizGuides.bundled();

  public VideoQuizService(LibraryService library) {
    this.library = library;
  }

  /** One entry of the quiz menu: a type's identifier and its label for this document. */
  public record TypeOption(String id, String label) {}

  /**
   * The scope as the form holds it: {@code kind} is {@code "whole"} or {@code "range"}, and the
   * times are the typed (or default) strings, always present so a form can be prefilled.
   */
  public record ScopeForm(String kind, String start, String end) {}

  /**
   * Everything a quiz menu needs in one reply: the menu {@code types}, the chosen {@code type} id,
   * the {@code scope} in use, whether it is {@code valid} (an unreadable range is not), the number
   * of lines it selects, whether that is {@code large} (above {@code largeAbove}) and the brief
   * with its suggested file name. When the scope is not valid, {@code lineCount} is 0 and {@code
   * text} and {@code fileName} are {@code null}.
   */
  public record Prepared(
      List<TypeOption> types,
      String type,
      ScopeForm scope,
      boolean valid,
      int lineCount,
      boolean large,
      int largeAbove,
      String text,
      String fileName) {}

  /**
   * Prepares the quiz for a video title.
   *
   * @param scope {@code "whole"} or {@code "range"}; {@code null} or blank applies the default rule
   *     (see {@link VideoQuizScope#defaultFor})
   * @param start range start as {@code m:ss} or {@code h:mm:ss}; {@code null} uses the default
   * @param end range end in the same form; {@code null} uses the default
   * @param type a {@link QuizType#id}; {@code null} or unknown means the first type
   * @param position the player's position in seconds
   * @param playerSeconds the player's duration, or {@code null} when not known
   * @throws IllegalArgumentException if the title is a comic or {@code scope} is unrecognised
   * @throws NoSuchElementException if the title is unknown
   */
  public Prepared prepare(
      String titleId,
      String scope,
      String start,
      String end,
      String type,
      double position,
      Double playerSeconds)
      throws IOException {
    var document = library.videoDocument(titleId);
    var fallback = VideoQuizScope.defaultFor(document, position, playerSeconds);
    var form = scope == null || scope.isBlank() ? fallback : formFor(scope, start, end, fallback);
    var chosen = QuizType.of(type);
    var types =
        Arrays.stream(QuizType.values())
            .map(each -> new TypeOption(each.id(), each.label(document)))
            .toList();
    var shown =
        new ScopeForm(form.kind().name().toLowerCase(Locale.ROOT), form.start(), form.end());
    var resolved = VideoQuizScope.of(form);
    if (resolved.isEmpty())
      return new Prepared(
          types, chosen.id(), shown, false, 0, false, VideoQuizScope.LARGE_LINES, null, null);
    var brief = VideoQuizBuilder.build(guides, document, resolved.get(), chosen);
    return new Prepared(
        types,
        chosen.id(),
        shown,
        true,
        brief.lineCount(),
        brief.large(),
        VideoQuizScope.LARGE_LINES,
        brief.text(),
        brief.fileName());
  }

  private static Form formFor(String scope, String start, String end, Form fallback) {
    Kind kind =
        switch (scope) {
          case "whole" -> Kind.WHOLE;
          case "range" -> Kind.RANGE;
          default -> throw new IllegalArgumentException("Unknown quiz scope: " + scope);
        };
    return new Form(
        kind, start == null ? fallback.start() : start, end == null ? fallback.end() : end);
  }
}
