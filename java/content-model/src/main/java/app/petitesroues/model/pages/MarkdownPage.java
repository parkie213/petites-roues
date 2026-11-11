package app.petitesroues.model.pages;

/**
 * A Markdown page from a folder at the root of a content repository: a general quiz in {@code
 * quizzes/} (a prompt handed to a chat app) or a study note in {@code notes/} (read in the
 * library). The app names it and passes its text on unchanged; it never writes it.
 *
 * @param repository the owning content root's opaque identity
 * @param repositoryName the owning repository's display name
 * @param id the page identifier without {@code .md}, unique within its collection
 * @param title the file's first nonempty {@code # } heading, or its file name without the extension
 * @param text the whole file
 */
public record MarkdownPage(
    String repository, String repositoryName, String id, String title, String text) {
  /** The folder holding general quiz prompts. */
  public static final String QUIZZES = "quizzes";

  /** The folder holding study notes. */
  public static final String NOTES = "notes";

  /** The first nonempty level-one heading, or the supplied file identifier as fallback. */
  public static String title(String markdown, String fallback) {
    return markdown
        .lines()
        .filter(line -> line.startsWith("# "))
        .map(line -> line.substring(2).strip())
        .filter(heading -> !heading.isEmpty())
        .findFirst()
        .orElse(fallback);
  }
}
