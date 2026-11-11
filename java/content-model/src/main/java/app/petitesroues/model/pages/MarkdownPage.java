package app.petitesroues.model.pages;

/**
 * A general quiz prompt or study note: its display title and unmodified Markdown text. Repository
 * identity and file location belong to the library entry that contains this content.
 */
public record MarkdownPage(String title, String text) {
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
