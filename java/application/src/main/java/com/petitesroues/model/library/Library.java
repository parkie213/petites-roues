package com.petitesroues.model.library;

import java.util.List;

/**
 * This computer's collection of registered content repositories and their browsable entries. Each
 * repository retains its folder structure through its entries' repository-relative document paths.
 *
 * <p>This is a snapshot assembled for browsing, with a defensive copy of the repository list. It
 * does not discover files or own study documents. Individually registered documents appear in the
 * flat catalogue rather than in this repository collection.
 *
 * @param repositories registered repositories paired with their entries, including missing ones
 */
public record Library(List<RepositoryContents> repositories) {
  public Library {
    repositories = List.copyOf(repositories);
  }

  /**
   * One repository and its registered entry summaries, with a defensive copy of the entry list.
   *
   * @param repository the content collection described by these entries
   * @param entries its browsable contents, including entries with unavailable files
   * @param media the folder's media that no study document references, with its warnings
   */
  public record RepositoryContents(
      ContentRepository repository, List<CatalogueItem> entries, List<MediaItem> media) {
    public RepositoryContents {
      entries = List.copyOf(entries);
      media = List.copyOf(media);
    }
  }
}
