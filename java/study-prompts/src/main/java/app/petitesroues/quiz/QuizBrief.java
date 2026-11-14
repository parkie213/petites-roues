package app.petitesroues.quiz;

import app.petitesroues.model.document.StudyDocument;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The Markdown skeleton shared by every quiz brief: heading, introduction, language roles, the
 * optional chosen quiz type, the demoted quiz rules, then the title's lines under one section.
 * Owned here so a chapter quiz and a video quiz cannot drift apart. Pure text assembly: no file
 * access, and learner notes never appear, only source text and translations. The output is
 * deterministic for equal inputs and always ends with a newline.
 */
final class QuizBrief {
  private QuizBrief() {}

  /** Keeps a quiz from working through the lines in order, as chat models otherwise do. */
  static final String SPREAD =
      "Pick items from all over these lines, in a mixed order: do not start at the beginning, "
          + "and do not take two items in a row from the same page or moment.";

  /**
   * One line of the quiz: an optional {@code label} (a time) before the stripped {@code source},
   * and an optional {@code translation} after an arrow. The source must not be blank.
   */
  record Entry(String label, String source, String translation) {}

  /** Lines shown together, under a {@code ###} {@code heading} when it is not {@code null}. */
  record Group(String heading, List<Entry> entries) {}

  /**
   * Assembles the brief. {@code type} is optional and appears above the rules. {@code emptyText}
   * replaces the lines when no group has an entry; otherwise the spread instruction precedes them.
   */
  static String render(
      String heading,
      String intro,
      StudyDocument document,
      ChapterQuizBuilder.Type type,
      String rules,
      String section,
      String emptyText,
      List<Group> groups) {
    var text = new StringBuilder();
    text.append("# ").append(heading).append("\n\n").append(intro).append("\n\n");
    text.append("Source language: `")
        .append(document.sourceLanguage())
        .append("`; translation language: `")
        .append(document.translationLanguage())
        .append("`; notes language: `")
        .append(document.notesLanguage())
        .append("`. Each line below is the source text, then → the app's translation.\n\n");
    if (type != null)
      text.append("## Quiz type: ")
          .append(type.label())
          .append("\n\n")
          .append(type.instructions())
          .append(
              "\n\nWhatever the type, keep the approach of the rules below: one item at a time, ")
          .append("wait for the attempt, diagnose, hint, retry, and reveal only when asked.\n\n");
    text.append("## Quiz rules\n\n")
        .append(demote(rules))
        .append("\n\n## ")
        .append(section)
        .append("\n\n");
    if (groups.stream().allMatch(group -> group.entries().isEmpty())) {
      return text.append(emptyText).append('\n').toString();
    }
    text.append(SPREAD).append("\n\n");
    for (var group : groups) {
      if (group.entries().isEmpty()) continue;
      if (group.heading() != null) text.append("\n### ").append(group.heading()).append("\n\n");
      for (var entry : group.entries()) {
        text.append("- ");
        if (entry.label() != null) text.append(entry.label()).append(' ');
        text.append(entry.source().strip());
        if (entry.translation() != null && !entry.translation().isBlank())
          text.append(" → ").append(entry.translation().strip());
        text.append('\n');
      }
    }
    return text.toString();
  }

  /** Nests a bundled file's headings two levels below this brief's sections. */
  static String demote(String markdown) {
    return markdown
        .strip()
        .lines()
        .map(line -> line.startsWith("#") ? "##" + line : line)
        .collect(Collectors.joining("\n"));
  }

  /** {@code quiz-<slug>.md} from the title and an optional part such as "chapter 2". */
  static String fileName(String title, String part) {
    String slug =
        (title + " " + part)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "-")
            .replaceAll("^-|-$", "");
    return "quiz-" + (slug.isEmpty() ? "title" : slug) + ".md";
  }
}
