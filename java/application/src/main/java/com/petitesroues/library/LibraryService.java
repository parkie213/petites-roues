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
   * Returns an indexed media file with this fingerprint that is still present, preferring the first
   * by folder and path; empty if none is.
   */
  public Optional<IndexedMedia> indexedMedia(String fingerprint) {
    return media.index().all().stream()
        .filter(item -> item.fingerprint().equals(fingerprint))
        .filter(item -> Files.isRegularFile(item.file()))
        .findFirst();
  }

  /**
   * Returns the title's own poster, which lives with its linked media ({@link
   * TitleMediaResolver#poster}); empty if the title is unknown, has no media or has no poster.
   *
   * @throws IllegalArgumentException if several posters are present
   */
  public Optional<Path> poster(String id) throws IOException {
    var item = repository.find(id).flatMap(media::media);
    return item.isEmpty() ? Optional.empty() : Optional.ofNullable(poster(item.get()));
  }

  /**
   * Returns the series poster for the title's linked media ({@link
   * TitleMediaResolver#seriesPoster}), or empty.
   *
   * @throws IllegalArgumentException if a folder has several posters
   */
  public Optional<Path> seriesPoster(String id) throws IOException {
    var item = repository.find(id).flatMap(media::media);
    return item.isEmpty() ? Optional.empty() : Optional.ofNullable(seriesPoster(item.get()));
  }

  /** The media file's own poster, or null. */
  public static Path poster(IndexedMedia item) throws IOException {
    return TitleMediaResolver.poster(item.file());
  }

  /** The media file's series poster within its registered folder, or null. */
  public static Path seriesPoster(IndexedMedia item) throws IOException {
    return TitleMediaResolver.seriesPoster(item.file(), Path.of(item.repositoryPath()));
  }

  /** Which posters a media file has, for browsing. */
  private record Posters(boolean own, boolean series) {
    static final Posters NONE = new Posters(false, false);

    /** An ambiguous folder counts as none here; requesting the card reports the problem. */
    static Posters of(IndexedMedia item) {
      try {
        return new Posters(poster(item) != null, seriesPoster(item) != null);
      } catch (IllegalArgumentException | IOException ambiguous) {
        return NONE;
      }
    }
  }

  /**
   * Lists each registered folder as it is on disk, including empty folders: the study documents
   * found in it and every video and CBZ found in it. A title links to its media by fingerprint,
   * possibly in another folder ({@link CatalogueItem#mediaRepositoryId}); a media item names the
   * title it belongs to, or none. Individually registered titles remain accessible through {@link
   * #catalogue()}. Reads registrations from the store; does not discover or refresh content.
   */
  public Library library() throws IOException {
    var titles = repository.all();
    var index = media.index();
    var entries = catalogue(titles, index);
    var owners = new HashMap<String, String>();
    for (var title : titles) {
      var location = title.location();
      if (location != null && !location.missing() && location.referenceFingerprint() != null)
        owners.putIfAbsent(
            mediaKind(location.documentType()) + location.referenceFingerprint(), title.id());
    }
    return new Library(
        repositories.list().stream()
            .map(
                repo ->
                    new Library.RepositoryContents(
                        repo,
                        entries.stream()
                            .filter(entry -> repo.id().equals(entry.repositoryId()))
                            .toList(),
                        index.all().stream()
                            .filter(item -> repo.id().equals(item.repositoryId()))
                            .map(item -> item(item, owners.get(item.kind() + item.fingerprint())))
                            .toList()))
            .toList());
  }

  private static String mediaKind(String documentType) {
    return "comic".equals(documentType) ? "comic" : "video";
  }

  private static MediaItem item(IndexedMedia item, String titleId) {
    var warnings = new ArrayList<String>();
    if (titleId == null) warnings.add("No study document");
    if (item.unplayableAudio()) warnings.add(item.audioWarning());
    var posters = Posters.of(item);
    return new MediaItem(
        item.repositoryId(),
        item.relativePath(),
        item.kind(),
        item.fingerprint(),
        titleId,
        item.durationSeconds(),
        posters.own(),
        posters.series(),
        warnings);
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
      var posters = matched.map(Posters::of).orElse(Posters.NONE);
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
              entry.shelf(),
              entry.sourceLanguage(),
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
              posters.own(),
              posters.series(),
              progress,
              matched.map(IndexedMedia::repositoryId).orElse(null),
              matched.map(IndexedMedia::relativePath).orElse(null)));
    }
    return List.copyOf(result);
  }
}
