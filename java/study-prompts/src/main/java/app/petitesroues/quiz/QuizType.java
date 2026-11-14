package app.petitesroues.quiz;

import app.petitesroues.model.document.StudyDocument;
import java.util.Locale;

/**
 * The quiz menu: the kinds of quiz a learner can ask for. Each type's {@link #id} is the stable
 * identifier clients send, and its {@link #heading} names its instructions section in the bundled
 * quiz-types.md, so ids, menu order and headings are owned here once.
 */
public enum QuizType {
  TO_SOURCE("to-source", "Translate into the source language"),
  TO_TRANSLATION("to-translation", "Translate into the translation language"),
  COMPREHENSION("comprehension", "Comprehension"),
  VOCABULARY("vocabulary", "Vocabulary"),
  TENSES("tenses", "Verb tenses"),
  MIXED("mixed", "Mixed");

  private final String id;
  private final String heading;

  QuizType(String id, String heading) {
    this.id = id;
    this.heading = heading;
  }

  /** The identifier clients send, such as {@code "to-source"}. */
  public String id() {
    return id;
  }

  /** The {@code ##} heading of this type's section in quiz-types.md. */
  public String heading() {
    return heading;
  }

  /**
   * The menu label for {@code document}, in English: the translation types name the document's
   * source or translation language, the others are fixed. A language without an English name shows
   * its tag, and a missing tag shows "Unknown language".
   */
  public String label(StudyDocument document) {
    return switch (this) {
      case TO_SOURCE -> "Translate into " + languageName(document.sourceLanguage());
      case TO_TRANSLATION -> "Translate into " + languageName(document.translationLanguage());
      case COMPREHENSION -> "Comprehension";
      case VOCABULARY -> "Vocabulary";
      case TENSES -> "Verb tenses";
      case MIXED -> "Mixed";
    };
  }

  /** The type with {@code id}; an unknown or {@code null} id gives {@link #TO_SOURCE}. */
  public static QuizType of(String id) {
    for (var type : values()) if (type.id.equals(id)) return type;
    return TO_SOURCE;
  }

  /**
   * This type's label and its instructions from {@code typesMarkdown} (the contents of
   * quiz-types.md), ready for a brief. A type with no section there gets empty instructions.
   */
  public ChapterQuizBuilder.Type chosen(String typesMarkdown, StudyDocument document) {
    for (var section : typesMarkdown.split("(?m)^## ", -1)) {
      if (section.startsWith(heading + "\n"))
        return new ChapterQuizBuilder.Type(
            label(document), section.substring(heading.length()).strip());
    }
    return new ChapterQuizBuilder.Type(label(document), "");
  }

  private static String languageName(String tag) {
    if (tag == null || tag.isBlank()) return "Unknown language";
    String name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH);
    return name.isBlank() ? tag : name;
  }
}
