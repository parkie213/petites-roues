package app.petitesroues.library;

import app.petitesroues.comic.ReadingProgressStore;
import app.petitesroues.library.linking.MediaLinks;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.library.shelves.MediaNames;
import app.petitesroues.library.shelves.Shelves;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.library.CatalogueItem;
import app.petitesroues.model.library.IndexedMedia;
import app.petitesroues.model.library.Library;
import app.petitesroues.model.library.MediaItem;
import app.petitesroues.model.library.PosterEntry;
import app.petitesroues.model.library.Posters;
import app.petitesroues.playback.PlaybackProgressStore;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.study.ReviewQueueService;
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
   * Returns the title's own poster, which lives with its linked media and was found by Refresh
   * ({@link app.petitesroues.library.discovery.ContentScanner}); empty if the title is unknown, has
   * no media or has no poster.
   */
  public Optional<Path> poster(String id) throws IOException {
    var item = repository.find(id).flatMap(media::media);
    return item.isEmpty() ? Optional.empty() : Optional.ofNullable(poster(item.get()));
  }

  /** Returns the series poster for the title's linked media, as Refresh found it, or empty. */
  public Optional<Path> seriesPoster(String id) throws IOException {
    var item = repository.find(id).flatMap(media::media);
    return item.isEmpty() ? Optional.empty() : Optional.ofNullable(seriesPoster(item.get()));
  }

  /** The media file's own poster as Refresh found it, or null when none or since removed. */
  public static Path poster(IndexedMedia item) {
    return present(item.poster());
  }

  /** The media file's series poster as Refresh found it, or null when none or since removed. */
  public static Path seriesPoster(IndexedMedia item) {
    return present(item.seriesPoster());
  }

  private static Path present(Path file) {
    return file != null && Files.isRegularFile(file) ? file : null;
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
            MediaLinks.key(
                TitleMediaService.kind(location.documentType()), location.referenceFingerprint()),
            title.id());
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
                        index.candidates().stream()
                            .filter(item -> repo.id().equals(item.media().repositoryId()))
                            .map(
                                item ->
                                    item(
                                        item.media(),
                                        MediaLinks.owner(item, owners::get),
                                        item.linkedTo() != null))
                            .toList()))
            .toList());
  }

  /**
   * The Posters view, grouped by the shared rules ({@link Shelves}): titles whose media was found,
   * in the folder holding that media, and media that no study document references, named from its
   * path. Choosing a language keeps only that language's titles, so media without a document is
   * hidden then. Choosing a folder keeps its cards; a folder with no cards shows them all.
   *
   * @param repositoryId the folder to show, or null for all
   * @param language a BCP 47 source language to show, or null for all
   */
  public Posters posters(String repositoryId, String language) throws IOException {
    var library = library();
    var titles =
        library.repositories().stream()
            .flatMap(contents -> contents.entries().stream())
            .filter(CatalogueItem::mediaAvailable)
            .filter(title -> language == null || language.equals(title.sourceLanguage()))
            .toList();
    Map<String, List<PosterEntry>> cards = new LinkedHashMap<>();
    for (var contents : library.repositories()) {
      String folder = contents.repository().id();
      List<PosterEntry> entries = new ArrayList<>();
      titles.stream()
          .filter(title -> folder.equals(title.mediaRepositoryId()))
          .map(PosterEntry::of)
          .forEach(entries::add);
      if (language == null)
        contents.media().stream()
            .filter(media -> media.titleId() == null)
            .map(PosterEntry::of)
            .forEach(entries::add);
      if (!entries.isEmpty()) cards.put(folder, entries);
    }
    var shown =
        cards.containsKey(repositoryId)
            ? cards.get(repositoryId)
            : cards.values().stream().flatMap(List::stream).toList();
    long languages =
        shown.stream().map(PosterEntry::sourceLanguage).filter(Objects::nonNull).distinct().count();
    return new Posters(
        Posters.of(Shelves.shelves(shown)), List.copyOf(cards.keySet()), languages > 1);
  }

  private static MediaItem item(IndexedMedia item, String titleId, boolean linkedByHand) {
    var warnings = new ArrayList<String>();
    if (titleId == null) warnings.add("No study document");
    if (item.unplayableAudio()) warnings.add(item.audioWarning());
    // Without a document nothing says which language is wanted, so say which one plays.
    if (titleId == null && item.languagesNote() != null) warnings.add(item.languagesNote());
    if (item.posterProblem() != null) warnings.add(item.posterProblem());
    var named = MediaNames.fromPath(item.relativePath());
    return new MediaItem(
        item.repositoryId(),
        item.relativePath(),
        named.series(),
        named.title(),
        item.kind(),
        item.fingerprint(),
        titleId,
        linkedByHand,
        item.durationSeconds(),
        item.posterPath() != null,
        item.seriesPosterPath() != null,
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
              ? "No reference media recorded"
              : "No " + (comic ? "CBZ" : "video") + " matches its reference";
      boolean available = documentAvailable && mediaAvailable;
      var problems = new LinkedHashMap<String, String>();
      if (!available)
        problems.put(
            "title", !documentAvailable ? "Document missing — refresh repository" : videoProblem);
      matched
          .filter(IndexedMedia::unplayableAudio)
          .ifPresent(item -> problems.put("audio", item.audioWarning()));
      matched
          .map(item -> item.languageWarning(entry.sourceLanguage()))
          .ifPresent(warning -> problems.put("language", warning));
      matched
          .map(IndexedMedia::posterProblem)
          .ifPresent(problem -> problems.put("poster", problem));
      boolean linkedByHand = matched.map(item -> index.linkedByHand(entry, item)).orElse(false);
      Double duration = details == null ? null : details.durationSeconds();
      if (linkedByHand)
        matched
            .map(item -> durationMismatch(duration, item.durationSeconds()))
            .ifPresent(problem -> problems.put("duration", problem));
      boolean poster = matched.map(item -> item.posterPath() != null).orElse(false);
      boolean seriesPoster = matched.map(item -> item.seriesPosterPath() != null).orElse(false);
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
              poster,
              seriesPoster,
              progress,
              matched.map(IndexedMedia::repositoryId).orElse(null),
              matched.map(IndexedMedia::relativePath).orElse(null),
              linkedByHand));
    }
    return List.copyOf(result);
  }

  /** How far a hand-linked video's length may differ from the document's before it is flagged. */
  static final double DURATION_TOLERANCE_SECONDS = 2;

  /**
   * The warning for a hand-linked video whose length differs from the one the document was timed
   * against, or null when they agree or either is unknown.
   */
  static String durationMismatch(Double document, Double media) {
    if (document == null || media == null) return null;
    if (Math.abs(document - media) <= DURATION_TOLERANCE_SECONDS) return null;
    return "Video length differs from the document's ("
        + clock(media)
        + " vs "
        + clock(document)
        + "): cue timings may be off";
  }

  private static String clock(double seconds) {
    long whole = Math.round(seconds);
    return whole / 60 + ":" + String.format("%02d", whole % 60);
  }
}
