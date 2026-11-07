package com.petitesroues.library;

import com.petitesroues.comic.ReadingProgressStore;
import com.petitesroues.library.discovery.TitleMediaResolver;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.CatalogueItem;
import com.petitesroues.model.library.IndexedMedia;
import com.petitesroues.model.library.Library;
import com.petitesroues.model.library.MediaItem;
import com.petitesroues.playback.PlaybackProgressStore;
import com.petitesroues.study.DocumentStore;
import com.petitesroues.study.ReviewQueueService;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Reads the SQLite catalogue and served documents (each file plus its queued player edits). Title
 * and media discovery belong to explicit Add/Refresh; a title's media is the indexed file whose
 * fingerprint matches its document's reference ({@link TitleMediaService}).
 */
@Service
public class LibraryService {
  private final CatalogueStore repository;
  private final ReviewQueueService served;
  private final ContentRepositoryStore repositories;
  private final DocumentStore documents;
  private final PlaybackProgressStore playback;
  private final ReadingProgressStore reading;
  private final TitleMediaService media;

  public LibraryService(
      CatalogueStore repository,
      ReviewQueueService served,
      ContentRepositoryStore repositories,
      DocumentStore documents,
      PlaybackProgressStore playback,
      ReadingProgressStore reading,
      TitleMediaService media) {
    this.repository = repository;
    this.served = served;
    this.repositories = repositories;
    this.documents = documents;
    this.playback = playback;
    this.reading = reading;
    this.media = media;
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
   * Returns the title's video or CBZ: the indexed file whose fingerprint matches its document's
   * reference. Empty if the title is unknown, records no reference, or no such file was found by
   * the last refresh.
   */
  public Optional<Path> media(String id) {
    return repository.find(id).flatMap(media::media).map(IndexedMedia::file);
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
   * Groups indexed titles by the registered folder they are shown in, including empty folders, with
   * each folder's media that no present document references. A title with linked media is shown in
   * the folder holding that media (its {@link CatalogueItem#mediaPath} places it there); one
   * without is shown where its document is. Individually registered titles remain accessible
   * through {@link #catalogue()}. Reads registrations from the store; does not discover or refresh
   * content.
   */
  public Library library() throws IOException {
    var titles = repository.all();
    var index = media.index();
    var entries = catalogue(titles, index);
    var referenced = new HashSet<String>();
    for (var title : titles)
      if (title.location() != null
          && !title.location().missing()
          && title.location().referenceFingerprint() != null)
        referenced.add(title.location().referenceFingerprint());
    return new Library(
        repositories.list().stream()
            .map(
                repo ->
                    new Library.RepositoryContents(
                        repo,
                        entries.stream().filter(entry -> repo.id().equals(shownIn(entry))).toList(),
                        index.all().stream()
                            .filter(item -> repo.id().equals(item.repositoryId()))
                            .filter(item -> !referenced.contains(item.fingerprint()))
                            .map(LibraryService::unmatched)
                            .toList()))
            .toList());
  }

  /** A title is shown where its media lives; without linked media, where its document is. */
  private static String shownIn(CatalogueItem entry) {
    return entry.mediaRepositoryId() != null ? entry.mediaRepositoryId() : entry.repositoryId();
  }

  private static MediaItem unmatched(IndexedMedia item) {
    var warnings = new ArrayList<String>();
    warnings.add("No study document");
    if (item.unplayableAudio()) warnings.add(item.audioWarning());
    return new MediaItem(
        item.repositoryId(), item.relativePath(), item.kind(), item.durationSeconds(), warnings);
  }

  /**
   * Returns an immutable list of all indexed title summaries, including individually registered and
   * missing titles. Metadata and media come from the index; only the document and the matched media
   * files are checked on disk. Documents are not decoded and repositories are not scanned or
   * refreshed. Missing documents and unmatched media are represented by availability flags and
   * title problems rather than omitting the title; a matched video whose audio no browser can play
   * stays available, with an {@code audio} problem.
   */
  public List<CatalogueItem> catalogue() throws IOException {
    return catalogue(repository.all(), media.index());
  }

  private List<CatalogueItem> catalogue(List<CatalogueEntry> titles, TitleMediaService.Index index)
      throws IOException {
    List<CatalogueItem> result = new ArrayList<>();
    var positions = playback.positions();
    var fractions = reading.fractions();
    for (var entry : titles) {
      var details = entry.location();
      String relativePath = details == null ? null : entry.documentPath();
      boolean documentAvailable =
          (details == null || !details.missing()) && Files.isRegularFile(entry.documentFile());
      boolean comic = details != null && "comic".equals(details.documentType());
      var matched = index.match(entry);
      boolean mediaAvailable = matched.isPresent();
      boolean videoAvailable = !comic && mediaAvailable;
      String videoProblem =
          details == null || details.referenceFingerprint() == null
              ? "No reference media recorded — run prepare/reference.py, then Refresh"
              : (comic ? "CBZ" : "Video")
                  + " not found — add the folder that holds it, then Refresh";
      boolean available = documentAvailable && mediaAvailable;
      var problems = new LinkedHashMap<String, String>();
      if (!available)
        problems.put(
            "title", !documentAvailable ? "Document missing — refresh repository" : videoProblem);
      matched
          .filter(IndexedMedia::unplayableAudio)
          .ifPresent(item -> problems.put("audio", item.audioWarning()));
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
              Collections.unmodifiableMap(problems),
              comic ? "comic" : "video",
              mediaAvailable,
              poster,
              seriesPoster,
              progress,
              matched.map(IndexedMedia::repositoryId).orElse(null),
              matched.map(IndexedMedia::relativePath).orElse(null)));
    }
    return List.copyOf(result);
  }
}
