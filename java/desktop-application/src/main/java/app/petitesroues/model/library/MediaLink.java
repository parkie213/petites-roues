package app.petitesroues.model.library;

/**
 * A media file the user linked to a title by hand. It is local library state, not part of the study
 * document, and wins over the fingerprint on both sides ({@link
 * app.petitesroues.library.linking.MediaLinks}). Keyed by the file's path, so it survives Refresh
 * and is lost when the file moves.
 *
 * @param repositoryId the registered folder holding the media
 * @param relativePath the media's path below that folder, with forward slashes
 * @param titleId the title it is linked to
 */
public record MediaLink(String repositoryId, String relativePath, String titleId) {}
