package app.petitesroues.model.library.documents;

import app.petitesroues.model.library.repository.Repository;

/**
 * An indexed study document owned by a repository, with its slash-separated relative file path.
 * Metadata describes the last successful read, not current availability. Its reference may link to
 * a media file in any repository. Standalone platform registrations are not repository entries.
 */
public record DocumentEntry(Repository repository, String path, DocumentMetadata metadata) {}
