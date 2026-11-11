package app.petitesroues.model.library;

import app.petitesroues.model.library.repository.Repository;

/**
 * A registered folder of learning content: study documents, notes and quizzes, media (videos and
 * CBZ comics), or any mix. Media links to documents by fingerprint, in this folder or another. A
 * repository may be a Git checkout, but Git is not required by this model.
 *
 * <p>This is the desktop registration and index summary; the shared identity and display name are
 * represented by {@link Repository}. The registration describes a local content root and its last
 * successful discovery result. Missing documents remain registered so their local learning state
 * survives temporary absence. Files may change after discovery; counts describe registered entries
 * rather than current file availability.
 *
 * @param id stable local registration identity, independent of the content folder's name
 * @param name display name chosen when registering the content directory
 * @param localPath absolute path to the local content root
 * @param lastRefreshedAt last successful discovery time in UTC, or {@code null} before discovery
 * @param titles number of registered entries belonging to this repository, including missing
 *     documents
 * @param missing number of registered entries whose documents were absent at the last refresh
 * @param media number of videos and CBZ files found at the last refresh
 */
public record ContentRepository(
    String id,
    String name,
    String localPath,
    String lastRefreshedAt,
    int titles,
    int missing,
    int media) {
  /** Creates a registration without indexed media. */
  public ContentRepository(
      String id, String name, String localPath, String lastRefreshedAt, int titles, int missing) {
    this(id, name, localPath, lastRefreshedAt, titles, missing, 0);
  }
}
