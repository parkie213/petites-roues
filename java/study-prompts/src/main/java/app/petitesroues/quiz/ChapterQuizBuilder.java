package app.petitesroues.quiz;

import app.petitesroues.model.document.StudyDocument;
import java.util.ArrayList;

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
    var groups = new ArrayList<QuizBrief.Group>();
    for (int offset = 0; offset < pages.size(); offset++) {
      var page = pages.get(offset);
      var entries =
          page.boxes().stream()
              .flatMap(box -> box.lines().stream())
              .filter(line -> !line.source().isBlank())
              .map(line -> new QuizBrief.Entry(null, line.source(), line.translation()))
              .toList();
      String label =
          page.label() == null || page.label().isBlank()
              ? Integer.toString(firstPage + offset + 1)
              : page.label();
      groups.add(new QuizBrief.Group("Page " + label, entries));
    }
    String text =
        QuizBrief.render(
            "Chapter quiz: " + document.title() + ", chapter " + name,
            "Run the subtitle quiz below for a learner, using the speech bubbles of the comic "
                + "chapter they are reading: in the rules, a subtitle or line means a bubble, and "
                + "the film means this comic. Bubbles are listed in reading order. Start the first "
                + "exercise straight away. Instructions about the browser player do not apply "
                + "here, and the rules mention files from the library (a teaching guide, "
                + "documents) that are not included: work from the lines below.",
            document,
            type,
            rules,
            "Bubbles in chapter "
                + name
                + " (pages "
                + (firstPage + 1)
                + "–"
                + (firstPage + pages.size())
                + ")",
            "None: this chapter's text has not been found yet.",
            groups);
    return new Brief(text, fileName(document, index));
  }

  /** Suggested Markdown filename for a numbered chapter or an unnumbered section. */
  public static String fileName(StudyDocument document, int index) {
    var chapter = document.comic().chapters().get(index);
    String part = chapter.number() == null ? chapter.title() : "chapter " + chapter.number();
    return QuizBrief.fileName(document.title(), part);
  }
}
