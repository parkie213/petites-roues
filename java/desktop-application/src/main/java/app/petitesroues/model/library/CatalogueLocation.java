package app.petitesroues.model.library;

/**
 * The repository context of a registered document: its owner, content root and last known reference
 * duration. {@link CatalogueEntry#documentPath()} is resolved against this root; the document path
 * itself belongs to the entry.
 *
 * <p>This groups information that is present for repository-owned entries and absent for standalone
 * registrations. It lets callers resolve a document and describe its reference without opening the
 * document. Availability recorded at refresh can differ from the files currently on disk.
 *
 * @param repositoryId stable local identity of the owning repository
 * @param repositoryName display name of the owning repository
 * @param repositoryPath absolute path to its local content root
 * @param missing whether the document was absent at the last successful repository refresh; {@code
 *     false} does not guarantee that the file still exists
 * @param durationSeconds optional reference-video duration in seconds, independent of the final
 *     subtitle's end time
 * @param documentType indexed document discriminator; avoids opening JSON during library browsing
 * @param referenceFingerprint the document's reference media fingerprint, which links it to indexed
 *     media with the same fingerprint; null when the document records none
 */
public record CatalogueLocation(
    String repositoryId,
    String repositoryName,
    String repositoryPath,
    boolean missing,
    Double durationSeconds,
    String documentType,
    String referenceFingerprint) {
  /** Creates metadata for a document without a recorded reference fingerprint. */
  public CatalogueLocation(
      String repositoryId,
      String repositoryName,
      String repositoryPath,
      boolean missing,
      Double durationSeconds,
      String documentType) {
    this(
        repositoryId, repositoryName, repositoryPath, missing, durationSeconds, documentType, null);
  }

  /** Creates video metadata for callers indexing the original video-only format. */
  public CatalogueLocation(
      String repositoryId,
      String repositoryName,
      String repositoryPath,
      boolean missing,
      Double durationSeconds) {
    this(repositoryId, repositoryName, repositoryPath, missing, durationSeconds, "video", null);
  }
}
