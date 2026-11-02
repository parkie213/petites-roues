package com.petitesroues.model.library;

import com.petitesroues.model.document.StudyDocument;
import java.nio.file.Path;

/**
 * A locally registered copy of a portable {@link StudyDocument}, with its identity, display
 * metadata and document location. Cues and learner notes belong to the document itself.
 *
 * <p>The local identity owns this copy's playback progress and reviews. Its portable document
 * identity may also occur in other repositories. Registration survives a missing file; this record
 * describes the registered copy, not a guarantee that its files are currently present.
 *
 * @param id stable local entry identity used by player URLs and title operations
 * @param documentId portable study-material identity, unique within its owning repository
 * @param title display title from the last registration or refresh
 * @param series optional series name
 * @param season optional season number
 * @param episode optional episode number
 * @param volume optional comic volume (tome) number
 * @param sourceUrl optional reference to the source material
 * @param documentPath document path relative to the owning repository, or absolute for a standalone
 *     registration
 * @param location repository context, or {@code null} for an individually registered document
 */
public record CatalogueEntry(
    String id,
    String documentId,
    String title,
    String series,
    Integer season,
    Integer episode,
    Integer volume,
    String sourceUrl,
    String documentPath,
    CatalogueLocation location) {
  /** Creates an entry whose local and portable identities initially coincide. */
  public CatalogueEntry(
      String id,
      String title,
      String series,
      Integer season,
      Integer episode,
      String sourceUrl,
      String documentPath,
      CatalogueLocation location) {
    this(id, id, title, series, season, episode, null, sourceUrl, documentPath, location);
  }

  /**
   * Resolves the document path against its repository root without accessing the filesystem.
   *
   * @return the normalized absolute document path; the file need not exist
   * @throws IllegalStateException if a standalone path is relative, or a repository path is
   *     absolute or escapes its content root
   */
  public Path documentFile() {
    Path path = Path.of(documentPath);
    if (location == null) {
      if (!path.isAbsolute())
        throw new IllegalStateException("Standalone document path must be absolute: " + path);
      return path.normalize();
    }
    Path root = Path.of(location.repositoryPath()).toAbsolutePath().normalize();
    if (path.isAbsolute() || !root.resolve(path).normalize().startsWith(root))
      throw new IllegalStateException("Document path must be relative to its repository: " + path);
    return root.resolve(path).normalize();
  }

  /** Creates a standalone entry whose local and portable identities initially coincide. */
  public CatalogueEntry(
      String id,
      String title,
      String series,
      Integer season,
      Integer episode,
      String sourceUrl,
      String documentPath) {
    this(id, id, title, series, season, episode, null, sourceUrl, documentPath, null);
  }
}
