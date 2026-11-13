package app.petitesroues.model.library;

/**
 * The accepted copy of a title's study document, as stored in the catalogue.
 *
 * @param json the exact bytes of the document file when it was imported
 * @param sha256 hex SHA-256 of {@code json}; the copy's revision
 * @param importedAt ISO-8601 instant of the import
 */
public record AcceptedDocument(byte[] json, String sha256, String importedAt) {}
