package app.petitesroues.model.library.pages;

import app.petitesroues.model.library.repository.Repository;
import app.petitesroues.model.pages.MarkdownPage;

/**
 * A Markdown page located in a repository. The slash-separated path is relative to that repository,
 * including its collection folder and .md extension. Repository identity plus path identifies the
 * entry; the page content itself has no storage identity.
 */
public record MarkdownPageEntry(Repository repository, String path, MarkdownPage content) {
  /** The repository folder holding general quiz prompts. */
  public static final String QUIZZES = "quizzes";

  /** The repository folder holding study notes. */
  public static final String NOTES = "notes";
}
