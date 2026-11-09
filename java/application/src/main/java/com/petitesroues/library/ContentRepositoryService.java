package com.petitesroues.library;

import com.petitesroues.documents.StudyDocumentReader;
import com.petitesroues.library.discovery.ContentRepositoryScanner;
import com.petitesroues.library.discovery.ContentScanner;
import com.petitesroues.library.discovery.MediaKind;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.library.persistence.MediaStore;
import com.petitesroues.library.progress.LibraryProgress;
import com.petitesroues.media.MediaProbe;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.model.library.IndexedMedia;
import com.petitesroues.study.ReviewQueue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Coordinates registration, refresh and removal of registered folders (repositories).
 *
 * <p>Add and Refresh delegate discovery to {@link ContentRepositoryScanner}. A folder may hold
 * study documents, media, or both. A {@code .json} file is a study document when {@link
 * StudyDocumentReader#isStudyDocument} says it claims to be one, whatever its name; each supplies
 * its portable document ID and metadata, and its path determines where the title appears in the
 * library. Every video and CBZ is indexed with its fingerprint and posters, which links it to a
 * document whose reference fingerprint matches, in any folder (see {@link TitleMediaService}). A
 * folder need not be a Git checkout. This service does not run Git commands or copy content files.
 *
 * <p>This service reads and validates discovered documents, and probes new or changed media, before
 * starting a database transaction. SQL and database mapping belong to {@link
 * ContentRepositoryStore} and {@link CatalogueStore}. This service owns workflow rules and
 * transaction boundaries. Index updates then register the titles, record repository membership and
 * reference metadata, mark absent titles as missing, and update the refresh timestamp together.
 * Missing titles retain their catalogue entries and pending reviews until the repository is
 * removed.
 *
 * <p>Ordinary browsing reads the SQLite index; media is probed only by Add and Refresh. Application
 * callers hold {@link LibraryUpdateService}'s monitor while adding, refreshing or removing a
 * repository to coordinate these operations with document edits.
 */
@Service
public class ContentRepositoryService {
  /**
   * @param localPath existing content directory to discover recursively
   * @param name display name; null or blank uses the directory name
   */
  public record Add(String localPath, String name) {}

  private final ContentRepositoryStore repositories;

  /** A discovered document path whose contents passed preflight validation. */
  private record DiscoveredDocument(Path documentPath) {}

  /** A folder's validated documents and probed media, ready to index in one transaction. */
  private record Discovered(List<DiscoveredDocument> documents, List<IndexedMedia> media) {}

  private final ContentRepositoryScanner scanner;
  private final StudyDocumentReader reader;
  private final TransactionTemplate transactions;
  private final RepositoryContentService registration;
  private final ReviewQueue queue;
  private final MediaStore media;
  private final MediaProbe probe;

  public ContentRepositoryService(
      ContentRepositoryStore repositories,
      ContentRepositoryScanner scanner,
      StudyDocumentReader reader,
      TransactionTemplate transactions,
      RepositoryContentService registration,
      ReviewQueue queue,
      MediaStore media,
      MediaProbe probe) {
    this.repositories = repositories;
    this.scanner = scanner;
    this.reader = reader;
    this.transactions = transactions;
    this.registration = registration;
    this.queue = queue;
    this.media = media;
    this.probe = probe;
  }

  /**
   * Lists registered repositories and indexed title/missing counts without accessing their files.
   */
  public List<ContentRepository> list() {
    return repositories.list();
  }

  /**
   * Discovers an existing content folder and registers it with its titles in one transaction.
   *
   * @return the registered repository and its initial index counts
   * @throws IllegalArgumentException if the folder is absent/already registered, a document is
   *     invalid, a document ID is duplicated within this repository, or a file is already indexed
   *     elsewhere
   * @throws IOException if the directory traversal or a document read fails
   */
  public ContentRepository add(Add request) throws IOException {
    return add(request, LibraryProgress.Listener.NONE);
  }

  /** {@link #add(Add)}, reporting progress as it scans, probes and reads. */
  public ContentRepository add(Add request, LibraryProgress.Listener progress) throws IOException {
    if (request == null || request.localPath() == null || request.localPath().isBlank())
      throw new IllegalArgumentException("Choose a repository directory");
    if (!Files.isDirectory(Path.of(request.localPath())))
      throw new IllegalArgumentException("Repository directory not found");
    Path root = Path.of(request.localPath()).toRealPath();
    if (list().stream().anyMatch(r -> Path.of(r.localPath()).equals(root)))
      throw new IllegalArgumentException("This repository is already registered");
    String id = UUID.randomUUID().toString();
    String name =
        request.name() == null || request.name().isBlank()
            ? root.getFileName().toString()
            : request.name().strip();
    var discovered = discover(root, name, List.of(), progress);
    progress.report(new LibraryProgress(name, "saving", 0, 0, null));
    transactions.executeWithoutResult(
        status -> {
          repositories.insert(id, name, root.toString());
          updateIndex(id, discovered);
        });
    return repositories.find(id);
  }

  /**
   * Rediscovers a registered folder and atomically updates its index. Documents absent from the
   * scan are marked missing; their rows are retained. A failed refresh leaves the previous index
   * and refresh timestamp intact.
   *
   * @throws NoSuchElementException if the repository ID is unknown
   * @throws IllegalArgumentException if the folder is missing or discovered documents violate
   *     validation, identity or ownership rules
   * @throws IOException if the directory traversal or a document read fails
   */
  public ContentRepository refresh(String id) throws IOException {
    return refresh(id, LibraryProgress.Listener.NONE);
  }

  /** {@link #refresh(String)}, reporting progress as it scans, probes and reads. */
  public ContentRepository refresh(String id, LibraryProgress.Listener progress)
      throws IOException {
    var repository = repositories.find(id);
    var discovered =
        discover(
            Path.of(repository.localPath()), repository.name(), media.inRepository(id), progress);
    progress.report(new LibraryProgress(repository.name(), "saving", 0, 0, null));
    transactions.executeWithoutResult(status -> updateIndex(id, discovered));
    return repositories.find(id);
  }

  /**
   * Refreshes every registered folder, each in its own transaction, in name order.
   *
   * @throws IllegalArgumentException naming the folder whose refresh failed; folders refreshed
   *     before it keep their new index
   */
  public List<ContentRepository> refreshAll() throws IOException {
    return refreshAll(LibraryProgress.Listener.NONE);
  }

  /** {@link #refreshAll()}, reporting each folder's progress in turn. */
  public List<ContentRepository> refreshAll(LibraryProgress.Listener progress) throws IOException {
    for (var repository : list()) {
      try {
        refresh(repository.id(), progress);
      } catch (IllegalArgumentException failure) {
        throw new IllegalArgumentException(
            repository.name() + ": " + failure.getMessage(), failure);
      }
    }
    return list();
  }

  /**
   * Walks the folder, reads its study documents and probes its media before any index transaction
   * starts. Media whose size and modification time match its previous index row keeps that row's
   * probe; anything else is probed now.
   */
  private Discovered discover(
      Path root, String folder, List<IndexedMedia> previous, LibraryProgress.Listener progress)
      throws IOException {
    progress.report(new LibraryProgress(folder, "scanning", 0, 0, null));
    var found = scanner.scan(root);
    int count = 0, total = found.media().size();
    Map<String, IndexedMedia> known =
        previous.stream()
            .collect(Collectors.toMap(IndexedMedia::relativePath, Function.identity()));
    List<IndexedMedia> probed = new ArrayList<>();
    for (var file : found.media()) {
      progress.report(new LibraryProgress(folder, "media", count++, total, file.relativePath()));
      String kind = file.kind().id();
      var old = known.get(file.relativePath());
      MediaProbe.Probe result =
          old != null
                  && old.kind().equals(kind)
                  && file.unchangedSince(old.sizeBytes(), old.modifiedMillis())
              ? new MediaProbe.Probe(
                  old.fingerprint(),
                  old.durationSeconds(),
                  old.audioCodecs(),
                  old.audioPlayable(),
                  old.audioLanguages())
              : file.kind() == MediaKind.VIDEO
                  ? probe.video(file.handle())
                  : probe.comic(file.handle());
      // Posters come from this walk even when the probe is reused: they change independently.
      probed.add(
          new IndexedMedia(
              null,
              root.toString(),
              file.relativePath(),
              kind,
              file.size(),
              file.modifiedMillis(),
              result.fingerprint(),
              result.durationSeconds(),
              result.audioCodecs(),
              result.audioPlayable(),
              result.audioLanguages(),
              file.poster() == null ? null : file.poster().relativePath(),
              file.seriesPoster() == null ? null : file.seriesPoster().relativePath(),
              file.posterProblem()));
    }
    return new Discovered(
        readDocuments(
            root,
            folder,
            found.documents().stream().map(ContentScanner.Found::handle).toList(),
            progress),
        probed);
  }

  /** Reads the files that claim to be study documents and rejects duplicate IDs. */
  private List<DiscoveredDocument> readDocuments(
      Path root, String folder, List<Path> candidates, LibraryProgress.Listener progress)
      throws IOException {
    List<DiscoveredDocument> documents = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    int count = 0;
    for (Path file : candidates) {
      progress.report(
          new LibraryProgress(
              folder,
              "documents",
              count++,
              candidates.size(),
              root.relativize(file).toString().replace('\\', '/')));
      if (!reader.isStudyDocument(file)) continue;
      StudyDocument document;
      try {
        document = reader.read(file);
      } catch (RuntimeException failure) {
        throw new IllegalArgumentException(
            "Invalid document at " + root.relativize(file) + ": " + failure.getMessage(), failure);
      }
      if (!ids.add(document.id()))
        throw new IllegalArgumentException("Duplicate title ID in repository: " + document.id());
      documents.add(new DiscoveredDocument(file));
    }
    return documents;
  }

  /**
   * Reconciles a complete discovery result inside the caller's transaction. Existing titles start
   * as missing; each discovered title is registered and marked present, and the folder's media
   * index is replaced. Registration assigns or retains each local entry ID and rereads the document
   * through {@link RepositoryContentService}. Checked read failures are wrapped so the enclosing
   * transaction rolls back.
   */
  private void updateIndex(String repositoryId, Discovered discovered) {
    repositories.markTitlesMissing(repositoryId);
    for (var item : discovered.documents()) {
      try {
        registration.register(
            new RepositoryContentService.Registration(item.documentPath().toString(), true),
            repositoryId);
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
    }
    media.replace(repositoryId, discovered.media());
    repositories.recordRefresh(repositoryId, Instant.now().toString());
  }

  /**
   * Unregisters the repository and its titles in one transaction. Foreign-key cascades remove their
   * local review state. Documents and media remain on disk. Refused while player edits are queued
   * but not yet written into the files, since removal would lose them.
   *
   * @throws NoSuchElementException if the repository ID is unknown
   */
  public void remove(String id) {
    repositories.find(id);
    transactions.executeWithoutResult(
        status -> {
          int unwritten = queue.unwrittenEdits(id);
          if (unwritten > 0)
            throw new IllegalArgumentException(
                unwritten
                    + " player edits are not yet in the document files. Have an agent process"
                    + " the review queue, or discard the edits, before removing this repository.");
          repositories.delete(id);
        });
  }
}
