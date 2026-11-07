package com.petitesroues.library;

import com.petitesroues.documents.StudyDocumentReader;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.model.document.ComicDocument;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoDocument;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.CatalogueLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indexes one existing {@code document.json} in this computer's SQLite catalogue so the application
 * can load and edit it by its stable title ID.
 *
 * <p>Registration reads and validates the study document, then inserts or updates its display
 * metadata and document location. The document remains the source of truth for subtitles and
 * learner notes; registration does not rewrite or copy it. Media locations follow the title-folder
 * contract and are not stored here.
 *
 * <p>Called by {@link ContentRepositoryService} during Add/Refresh, which owns discovery,
 * repository reconciliation and its transaction boundary. This service saves the title row,
 * including its ownership and reference-edition metadata. Repository-scoped replacements retain the
 * local entry ID and must remain inside that repository. Portable IDs may recur in different
 * repositories; local IDs identify independent copies.
 */
@Service
public class RepositoryContentService {
  private final ContentRepositoryStore repositories;
  private final CatalogueStore repository;
  private final StudyDocumentReader reader;

  public RepositoryContentService(
      ContentRepositoryStore repositories, CatalogueStore repository, StudyDocumentReader reader) {
    this.repositories = repositories;
    this.repository = repository;
    this.reader = reader;
  }

  /**
   * An explicit request to index a document already on disk.
   *
   * @param documentPath absolute path to a file named {@code document.json}
   * @param replace whether an existing entry in the target scope with the document's ID may be
   *     updated; does not authorize overwriting the document file
   */
  public record Registration(String documentPath, boolean replace) {}

  /** Indexes a discovered document relative to its owning repository within Add/Refresh. */
  @Transactional
  public synchronized CatalogueEntry register(Registration request, String repositoryId)
      throws IOException {
    return index(request, repositoryId);
  }

  private CatalogueEntry index(Registration request, String repositoryId) throws IOException {
    if (request == null || request.documentPath() == null)
      throw new IllegalArgumentException("Document path is required");
    Path file = Path.of(request.documentPath());
    if (!file.isAbsolute()) throw new IllegalArgumentException("Document path must be absolute");
    if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Missing file: " + file);
    file = file.toRealPath();
    if (!file.getFileName().toString().equals("document.json"))
      throw new IllegalArgumentException("Title document must be named document.json");
    StudyDocument document = reader.read(file);
    CatalogueEntry previous = repository.findDocument(repositoryId, document.id()).orElse(null);
    if (previous != null && !request.replace()) throw new TitleExistsException();
    // One physical file must not acquire independent edit histories through overlapping roots.
    for (var entry : repository.all()) {
      if (entry.documentFile().equals(file)
          && (previous == null || !entry.id().equals(previous.id())))
        throw new IllegalArgumentException(
            "Document file already belongs to another library entry");
    }
    // Keep readable IDs when available, but never use portable identity as a global lookup key.
    String entryId =
        previous != null
            ? previous.id()
            : repository.contains(document.id()) ? UUID.randomUUID().toString() : document.id();
    var owningRepository = repositories.find(repositoryId);
    Path root = Path.of(owningRepository.localPath()).toRealPath();
    if (!file.startsWith(root))
      throw new IllegalArgumentException("Document is outside its owning repository");
    String storedPath = root.relativize(file).toString().replace('\\', '/');
    var video = document.body() instanceof VideoDocument v ? v : null;
    var comic = document.body() instanceof ComicDocument c ? c : null;
    var location =
        new CatalogueLocation(
            repositoryId,
            owningRepository.name(),
            root.toString(),
            false,
            document.edition(),
            video == null ? null : video.durationSeconds(),
            video == null ? "comic" : "video");
    var entry =
        new CatalogueEntry(
            entryId,
            document.id(),
            document.title(),
            document.series(),
            video == null ? null : video.season(),
            video == null ? null : video.episode(),
            comic == null ? null : comic.volume(),
            document.sourceUrl(),
            storedPath,
            location);
    repository.saveTitle(entry);
    return entry;
  }
}
