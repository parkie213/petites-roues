package app.petitesroues.quiz;

import app.petitesroues.model.document.StudyDocument;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds a self-contained chapter quiz brief from document content and supplied teaching rules.
 * Performs no file access, persistence, sharing or model calls. Learner notes are deliberately
 * excluded; only source text and translations are included, in manifest reading order.
 */
public final class ChapterQuizBuilder {
  private ChapterQuizBuilder() {}

  /** A selected quiz type's display label and instructions, supplied by the caller's quiz menu. */
  public record Type(String label, String instructions) {
    public Type {
      if (label == null || instructions == null)
        throw new IllegalArgumentException("Quiz type label and instructions are required");
    }
  }

  /** Prepared content and suggested file name; platforms decide how to export it. */
  public record Brief(String text, String fileName) {}

  /** Builds exactly the selected chapter; optional type instructions precede the supplied rules. */
  public static Brief build(String rules, StudyDocument document, int index, Type type) {
    if (rules == null) throw new IllegalArgumentException("Quiz rules are required");
    var comic = document.comic();
    var pages = comic.pagesForChapter(index);
    var chapter = comic.chapters().get(index);
    int firstPage = comic.pages().indexOf(pages.get(0));
    String name =
        chapter.number() == null ? chapter.title() : chapter.number() + " · " + chapter.title();
    var text = new StringBuilder();
    text.append("# Chapter quiz: ")
        .append(document.title())
        .append(", chapter ")
        .append(name)
        .append("\n\n");
    text.append(
        "Run the subtitle quiz below for a learner, using the speech bubbles of the comic "
            + "chapter they are reading: in the rules, a subtitle or line means a bubble, and the "
            + "film means this comic. Bubbles are listed in reading order. Start the first exercise "
            + "straight away. Instructions about the browser player do not apply here, and the "
            + "rules mention files from the library (a teaching guide, documents) that are not "
            + "included: work from the lines below.\n\n");
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
        .append(
            rules
                .strip()
                .lines()
                .map(line -> line.startsWith("#") ? "##" + line : line)
                .collect(Collectors.joining("\n")))
        .append("\n\n## Bubbles in chapter ")
        .append(name)
        .append(" (pages ")
        .append(firstPage + 1)
        .append("–")
        .append(firstPage + pages.size())
        .append(")\n\n");
    boolean hasLines =
        pages.stream()
            .flatMap(page -> page.boxes().stream())
            .flatMap(box -> box.lines().stream())
            .anyMatch(line -> !line.source().isBlank());
    if (!hasLines) text.append("None: this chapter's text has not been found yet.\n");
    else {
      text.append(
              "Pick items from all over these lines, in a mixed order: do not start at the beginning, ")
          .append("and do not take two items in a row from the same page or moment.\n\n");
      for (int offset = 0; offset < pages.size(); offset++) {
        var page = pages.get(offset);
        var lines =
            page.boxes().stream()
                .flatMap(box -> box.lines().stream())
                .filter(line -> !line.source().isBlank())
                .toList();
        if (lines.isEmpty()) continue;
        String label =
            page.label() == null || page.label().isBlank()
                ? Integer.toString(firstPage + offset + 1)
                : page.label();
        text.append("\n### Page ").append(label).append("\n\n");
        for (var line : lines) {
          text.append("- ").append(line.source().strip());
          if (line.translation() != null && !line.translation().isBlank())
            text.append(" → ").append(line.translation().strip());
          text.append('\n');
        }
      }
    }
    return new Brief(text.toString(), fileName(document, index));
  }

  /** Suggested Markdown filename for a numbered chapter or an unnumbered section. */
  public static String fileName(StudyDocument document, int index) {
    var chapter = document.comic().chapters().get(index);
    String part = chapter.number() == null ? chapter.title() : "chapter " + chapter.number();
    String slug =
        (document.title() + " " + part)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "-")
            .replaceAll("^-|-$", "");
    return "quiz-" + (slug.isEmpty() ? "title" : slug) + ".md";
  }
}
