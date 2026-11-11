package app.petitesroues.model.pages;

/**
 * A Markdown page from a folder at the root of a content repository: a general quiz in {@code
 * quizzes/} (a prompt handed to a chat app) or a study note in {@code notes/} (read in the
 * library). The app names it and passes its text on unchanged; it never writes it.
 *
 * @param repository the owning repository's registration ID
 * @param repositoryName the owning repository's display name
 * @param id the file name without {@code .md}, unique within its folder
 * @param title the file's first {@code # } heading, or {@code id} when it has none
 * @param text the whole file
 */
public record MarkdownPage(
    String repository, String repositoryName, String id, String title, String text) {}
