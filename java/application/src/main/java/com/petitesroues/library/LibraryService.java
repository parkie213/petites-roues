package com.petitesroues.library;

import com.petitesroues.comic.ReadingProgressStore;
import com.petitesroues.library.discovery.TitleMediaResolver;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.CatalogueItem;
import com.petitesroues.model.library.Library;
import com.petitesroues.playback.PlaybackProgressStore;
import com.petitesroues.study.DocumentStore;
import com.petitesroues.study.ReviewQueueService;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Reads the SQLite catalogue and served documents (each file plus its queued player edits). Title
 * discovery belongs to explicit Add/Refresh; media is checked in each title folder.
 */
@Service
public class LibraryService {
  private final CatalogueStore repository;
  private final ReviewQueueService served;
  private final ContentRepositoryStore repositories;
  private final DocumentStore documents;
  private final PlaybackProgressStore playback;
  private final ReadingProgressStore reading;

  public LibraryService(
      CatalogueStore repository,
      ReviewQueueService served,
      ContentRepositoryStore repositories,
      DocumentStore documents,
      PlaybackProgressStore playback,
      ReadingProgressStore reading) {
    this.repository = repository;
    this.served = served;
    this.repositories = repositories;
    this.documents = documents;
    this.playback = playback;
    this.reading = reading;
  }

  public boolean contains(String id) throws IOException {
    return repository.contains(id);
  }

  /** The document as the player sees it: the file with pending queued edits replayed. */
  public StudyDocument document(String id) throws IOException {
    return served.document(
        repository.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id)));
  }

  /**
   * Checks a candidate replacement for the title's document file without writing it. Returns every
   * structural problem; empty means the file would load.
   */
  public List<String> problems(String id, byte[] candidate) throws IOException {
    return documents.problems(
        repository.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id)),
        candidate);
  }

  /** Loads a video for subtitle operations; comic documents cannot be edited as timed cues. */
  public StudyDocument videoDocument(String id) throws IOException {
    var document = document(id);
    document.video();
    return document;
  }

  /**
   * Returns the title's video, or empty if the title is unknown or no video is present.
   *
   * @throws IllegalArgumentException if media is ambiguous or outside the title folder
   * @throws IOException if the title folder cannot be read
   */
  public Optional<Path> media(String id) throws IOException {
    var entry = repository.find(id);
    if (entry.isEmpty()) return Optional.empty();

    return Optional.ofNullable(TitleMediaResolver.video(entry.get().documentFile()));
  }

  /**
   * Returns the title's own {@code poster.*}, or empty if the title is unknown or has none.
   *
   * @throws IllegalArgumentException if several posters are present
   */
  public Optional<Path> poster(String id) throws IOException {
    var entry = repository.find(id);
    if (entry.isEmpty()) return Optional.empty();
    return Optional.ofNullable(TitleMediaResolver.poster(entry.get().documentFile()));
  }

  /**
   * Returns the nearest series poster above the title's folder within its repository, or empty for
   * unknown and standalone titles.
   *
   * @throws IllegalArgumentException if a folder has several posters
   */
  public Optional<Path> seriesPoster(String id) throws IOException {
    var entry = repository.find(id);
    return entry.isEmpty() ? Optional.empty() : Optional.ofNullable(seriesPoster(entry.get()));
  }

  private static Path seriesPoster(CatalogueEntry entry) throws IOException {
    var location = entry.location();
    if (location == null) return null;
    return TitleMediaResolver.seriesPoster(
        entry.documentFile(), Path.of(location.repositoryPath()));
  }

  /**
   * Groups indexed titles by registered repository, including empty repositories. Individually
   * registered titles remain accessible through {@link #catalogue()}. Reads registrations from the
   * store; does not discover or refresh content.
   */
  public Library library() throws IOException {
    var entries = catalogue();
    return new Library(
        repositories.list().stream()
            .map(
                repo ->
                    new Library.RepositoryContents(
                        repo,
                        entries.stream()
                            .filter(entry -> repo.id().equals(entry.repositoryId()))
                            .toList()))
            .toList());
  }

  /**
   * Returns an immutable list of all indexed title summaries, including individually registered and
   * missing titles. Metadata comes from the index; document presence and media availability are
   * checked only in indexed title folders. Documents are not decoded and repositories are not
   * scanned or refreshed. Missing documents and missing, ambiguous or unreadable media are
   * represented by availability flags and title problems rather than omitting the title.
   */
  public List<CatalogueItem> catalogue() throws IOException {
    List<CatalogueItem> result = new ArrayList<>();
    var positions = playback.positions();
    var fractions = reading.fractions();
    for (var entry : repository.all()) {
      var details = entry.location();
      String relativePath = details == null ? null : entry.documentPath();
      boolean documentAvailable =
          (details == null || !details.missing()) && Files.isRegularFile(entry.documentFile());
      boolean comic = details != null && "comic".equals(details.documentType());
      boolean videoAvailable = false;
      boolean mediaAvailable = false;
      String videoProblem = comic ? "CBZ not downloaded" : "Video not downloaded";
      try {
        mediaAvailable =
            (comic
                    ? TitleMediaResolver.comic(entry.documentFile())
                    : TitleMediaResolver.video(entry.documentFile()))
                != null;
        videoAvailable = !comic && mediaAvailable;
      } catch (IllegalArgumentException | IOException failure) {
        videoProblem = failure.getMessage();
      }
      boolean available = documentAvailable && mediaAvailable;
      boolean poster = false;
      boolean seriesPoster = false;
      try {
        poster = TitleMediaResolver.poster(entry.documentFile()) != null;
        seriesPoster = seriesPoster(entry) != null;
      } catch (IllegalArgumentException | IOException ambiguous) {
        // Browsing shows the placeholder; requesting the card reports the problem.
      }
      Double duration = details == null ? null : details.durationSeconds();
      Double position = positions.get(entry.id());
      Double progress =
          comic
              ? fractions.get(entry.id())
              : position == null || position <= 0 || duration == null || duration <= 0
                  ? null
                  : Math.min(1, position / duration);
      result.add(
          new CatalogueItem(
              entry.id(),
              entry.documentId(),
              entry.title(),
              entry.series(),
              entry.season(),
              entry.episode(),
              entry.volume(),
              entry.sourceUrl(),
              details == null ? null : details.repositoryId(),
              details == null ? null : details.repositoryName(),
              relativePath,
              duration,
              documentAvailable,
              videoAvailable,
              available
                  ? Map.of()
                  : Map.of(
                      "title",
                      !documentAvailable ? "Document missing — refresh repository" : videoProblem),
              comic ? "comic" : "video",
              mediaAvailable,
              poster,
              seriesPoster,
              progress));
    }
    return List.copyOf(result);
  }
}
