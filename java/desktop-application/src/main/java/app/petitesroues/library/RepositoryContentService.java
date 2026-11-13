package app.petitesroues.library;

import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.library.CatalogueLocation;
import app.petitesroues.model.library.documents.DocumentMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indexes one existing study document (a JSON file of any name) in this computer's SQLite catalogue
 * so the application can load and edit it by its stable title ID.
 *
 * <p>Registration takes the already-validated study document, then inserts or updates its display
 * metadata and document location. The document remains the source of truth for subtitles and
 * learner notes; registration does not rewrite or copy it. Its reference fingerprint is stored so
 * that indexed media can be matched to it; media itself is indexed by Refresh.
 *
 * <p>Called by {@link ContentRepositoryService} during Add/Refresh, which owns discovery,
 * repository reconciliation and its transaction boundary. This service saves the title row,
 * including its ownership and reference metadata. Repository-scoped replacements retain the local
 * entry ID and must remain inside that repository. Portable IDs may recur in different
 * repositories; local IDs identify independent copies.
 */
@Service
public class RepositoryContentService {
  private final ContentRepositoryStore repositories;
  private final CatalogueStore repository;

  public RepositoryContentService(ContentRepositoryStore repositories, CatalogueStore repository) {
    this.repositories = repositories;
    this.repository = repository;
  }

  /**
   * An explicit request to index a document already on disk.
   *
   * @param documentPath absolute path to the study document's JSON file
   * @param replace whether an existing entry in the target scope with the document's ID may be
   *     updated; does not authorize overwriting the document file
   */
  public record Registration(String documentPath, boolean replace) {}

  /**
   * Indexes a discovered document relative to its owning repository within Add/Refresh. {@code
   * document} is the already-decoded content of the file at {@code request.documentPath()}; the
   * file's contents are not read again.
   */
  @Transactional
  public synchronized CatalogueEntry register(
      Registration request, StudyDocument document, String repositoryId) throws IOException {
    return index(request, document, repositoryId);
  }

  /**
   * Updates an indexed title's display and reference metadata from its newly accepted document,
   * keeping its identity, location and missing flag. The caller owns the transaction.
   */
  public CatalogueEntry updateMetadata(CatalogueEntry entry, StudyDocument document) {
    var location = entry.location();
    var updated =
        entryFor(
            entry.id(),
            entry.documentPath(),
            location.repositoryId(),
            location.repositoryName(),
            location.repositoryPath(),
            location.missing(),
            document);
    repository.saveTitle(updated);
    return updated;
  }

  private CatalogueEntry index(Registration request, StudyDocument document, String repositoryId)
      throws IOException {
    if (request == null || request.documentPath() == null)
      throw new IllegalArgumentException("Document path is required");
    Path file = Path.of(request.documentPath());
    if (!file.isAbsolute()) throw new IllegalArgumentException("Document path must be absolute");
    if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Missing file: " + file);
    file = file.toRealPath();
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
    var entry =
        entryFor(
            entryId,
            storedPath,
            repositoryId,
            owningRepository.name(),
            root.toString(),
            false,
            document);
    repository.saveTitle(entry);
    return entry;
  }

  /** The title row for {@code document}: its metadata at the given identity and location. */
  private static CatalogueEntry entryFor(
      String entryId,
      String storedPath,
      String repositoryId,
      String repositoryName,
      String repositoryPath,
      boolean missing,
      StudyDocument document) {
    var metadata = DocumentMetadata.from(document);
    var location =
        new CatalogueLocation(
            repositoryId,
            repositoryName,
            repositoryPath,
            missing,
            metadata.durationSeconds(),
            metadata.kind().id(),
            metadata.referenceFingerprint());
    return new CatalogueEntry(
        entryId,
        metadata.documentId(),
        metadata.title(),
        metadata.series(),
        metadata.season(),
        metadata.episode(),
        metadata.volume(),
        metadata.shelf(),
        metadata.sourceLanguage(),
        metadata.sourceUrl(),
        storedPath,
        location);
  }
}
