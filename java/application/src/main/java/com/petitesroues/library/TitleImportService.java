package com.petitesroues.library;

import com.petitesroues.comic.CbzReader;
import com.petitesroues.documents.StudyDocumentValidator;
import com.petitesroues.library.discovery.TitleMediaResolver;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.media.Ffmpeg;
import com.petitesroues.model.document.ComicDocument;
import com.petitesroues.model.document.ComicPage;
import com.petitesroues.model.document.ReadingDirection;
import com.petitesroues.model.document.StudyDocument;
import com.petitesroues.model.document.VideoDocument;
import com.petitesroues.study.DocumentStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Creates a new title from a video or CBZ on this computer: a folder in a registered repository
 * holding a browser-playable copy of the media and an empty {@code document.json}, then indexes it.
 * Transcription, comic text and notes are left to the preparation scripts and agents. The original
 * file is never changed or moved, and an existing document is never replaced.
 *
 * <p>Copying or converting the media can take minutes, so {@link #start} validates the request and
 * then does that work on a background thread, reporting progress through {@link #job}; browsers
 * drop requests that stay silent that long. Only the final repository refresh holds {@link
 * LibraryUpdateService}'s monitor, as other repository changes do.
 */
@Service
public class TitleImportService {
  private static final Pattern FOLDER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ -]*");
  private static final List<String> IMAGES = List.of(".jpg", ".jpeg", ".png", ".webp");
  private static final System.Logger LOG = System.getLogger(TitleImportService.class.getName());

  private final ContentRepositoryStore repositories;
  private final CatalogueStore catalogue;
  private final ContentRepositoryService refresh;
  private final DocumentStore documents;
  private final CbzReader cbz;
  private final Ffmpeg ffmpeg;
  private final LibraryUpdateService updates;
  private final Map<String, Job> jobs = new ConcurrentHashMap<>();
  private final Set<Path> importing = ConcurrentHashMap.newKeySet();

  public TitleImportService(
      ContentRepositoryStore repositories,
      CatalogueStore catalogue,
      ContentRepositoryService refresh,
      DocumentStore documents,
      CbzReader cbz,
      Ffmpeg ffmpeg,
      LibraryUpdateService updates) {
    this.repositories = repositories;
    this.catalogue = catalogue;
    this.refresh = refresh;
    this.documents = documents;
    this.cbz = cbz;
    this.ffmpeg = ffmpeg;
    this.updates = updates;
  }

  /**
   * What the import dialog needs to know about a file.
   *
   * @param kind {@code video} or {@code comic}
   * @param name file name without extension, a starting point for the title and folder
   * @param audioTracks a video's audio tracks; empty for comics
   * @param pages a comic's page count; zero for videos
   */
  public record Inspection(
      String kind, String name, List<Ffmpeg.AudioTrack> audioTracks, int pages) {}

  /**
   * A new title.
   *
   * @param path the video or CBZ on this computer
   * @param repositoryId registered repository to create the title in
   * @param folder title folder relative to the repository root, such as {@code peppa/s01e02}; its
   *     last segment becomes the document ID
   * @param audioTrack ffprobe index of the audio track to keep; videos only, optional when the
   *     video has one track
   * @param rightToLeft comic reading direction; comics only
   */
  public record Create(
      String path,
      String repositoryId,
      String folder,
      String title,
      String sourceLanguage,
      String translationLanguage,
      String notesLanguage,
      Integer audioTrack,
      Boolean rightToLeft) {}

  public record Created(String titleId, String documentPath) {}

  /**
   * A started import. Jobs are kept in memory until the server stops, so a restart forgets them.
   *
   * @param status {@code RUNNING} until the title is indexed or the import fails
   * @param created the new title once {@code DONE}
   * @param error why the import stopped once {@code FAILED}
   */
  public record Job(Status status, Created created, String error) {
    public enum Status {
      RUNNING,
      DONE,
      FAILED
    }
  }

  /** A validated request: everything needed to write the title. */
  private record Plan(
      String id,
      Path root,
      String repositoryId,
      Path source,
      Path folder,
      Path document,
      StudyDocument created,
      boolean comic,
      int audioTrack) {}

  public Inspection inspect(String path) throws IOException {
    Path file = source(path);
    String name = file.getFileName().toString().replaceFirst("\\.[^.]*$", "");
    if (comic(file)) return new Inspection("comic", name, List.of(), pages(file).size());
    var tracks = ffmpeg.audioTracks(file);
    if (tracks.isEmpty()) throw new IllegalArgumentException("This video has no audio track");
    return new Inspection("video", name, tracks, 0);
  }

  /**
   * Checks the request, then copies or converts the media, writes the empty document and indexes
   * the title on a background thread. Problems with the request are thrown here; problems while
   * writing are reported by {@link #job}.
   *
   * @return the job ID to pass to {@link #job}
   * @throws IllegalArgumentException if the request cannot create a title, including a folder that
   *     another import is still writing
   */
  public String start(Create request) throws IOException {
    Plan plan = plan(request);
    if (!importing.add(plan.folder()))
      throw new IllegalArgumentException("That folder is already being imported");
    String job = UUID.randomUUID().toString();
    jobs.put(job, new Job(Job.Status.RUNNING, null, null));
    try {
      Thread.ofVirtual().name("import-" + plan.id()).start(() -> run(job, plan));
    } catch (RuntimeException failure) {
      jobs.remove(job);
      importing.remove(plan.folder());
      throw failure;
    }
    return job;
  }

  /**
   * The progress of a started import.
   *
   * @throws NoSuchElementException if no job has this ID, as after a server restart
   */
  public Job job(String id) {
    Job job = jobs.get(id);
    if (job == null)
      throw new NoSuchElementException("Unknown import " + id + "; the server may have restarted");
    return job;
  }

  private void run(String job, Plan plan) {
    try {
      jobs.put(job, new Job(Job.Status.DONE, write(plan), null));
    } catch (IOException | RuntimeException failure) {
      LOG.log(System.Logger.Level.ERROR, "Import of " + plan.source() + " failed", failure);
      String message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
      jobs.put(job, new Job(Job.Status.FAILED, null, message));
    } finally {
      importing.remove(plan.folder());
    }
  }

  private Plan plan(Create request) throws IOException {
    if (request == null) throw new IllegalArgumentException("Describe the title to create");
    Path file = source(request.path());
    var repository = repositories.find(request.repositoryId());
    Path root = Path.of(repository.localPath()).toRealPath();
    Path folder = folder(root, request.folder());
    String id =
        folder.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    id = id.replaceAll("^-|-$", "");
    if (catalogue.findDocument(repository.id(), id).isPresent())
      throw new IllegalArgumentException("This repository already has a title with ID " + id);
    Path document = folder.resolve("document.json");
    if (Files.exists(document))
      throw new IllegalArgumentException("That folder already has a document.json");
    if (Files.isDirectory(folder))
      try (var existing = Files.list(folder)) {
        if (existing.anyMatch(f -> comic(f) || TitleMediaResolver.isVideo(f)))
          throw new IllegalArgumentException("That folder already has a video or comic");
      }

    boolean comic = comic(file);
    var created =
        new StudyDocument(
            id,
            request.title() == null ? "" : request.title().strip(),
            language(request.sourceLanguage(), "fr"),
            language(request.translationLanguage(), "en"),
            language(request.notesLanguage(), "en"),
            null,
            null,
            null,
            comic
                ? comicBody(file, request)
                : new VideoDocument(null, null, null, null, List.of()));
    int audioTrack = 0;
    if (!comic) {
      var tracks = ffmpeg.audioTracks(file);
      if (request.audioTrack() != null) audioTrack = request.audioTrack();
      else if (tracks.size() == 1) audioTrack = tracks.getFirst().index();
      else throw new IllegalArgumentException("Choose which audio track to keep");
      int chosen = audioTrack;
      if (tracks.stream().noneMatch(track -> track.index() == chosen))
        throw new IllegalArgumentException("No audio track " + chosen);
    }
    StudyDocumentValidator.validate(created);
    return new Plan(id, root, repository.id(), file, folder, document, created, comic, audioTrack);
  }

  private Created write(Plan plan) throws IOException {
    Files.createDirectories(plan.folder());
    Path media = null;
    try {
      media =
          plan.comic()
              ? Files.copy(plan.source(), plan.folder().resolve(plan.source().getFileName()))
              : ffmpeg.playableCopy(
                  plan.source(), plan.audioTrack(), plan.folder().resolve("video"));
      documents.create(plan.document(), plan.created());
    } catch (IOException | RuntimeException failure) {
      if (media != null) Files.deleteIfExists(media);
      throw failure;
    }
    synchronized (updates) {
      refresh.refresh(plan.repositoryId());
      var entry = catalogue.findDocument(plan.repositoryId(), plan.id()).orElseThrow();
      return new Created(entry.id(), plan.root().relativize(plan.document()).toString());
    }
  }

  private static Path source(String path) throws IOException {
    if (path == null || path.isBlank()) throw new IllegalArgumentException("Choose a file");
    Path file = Path.of(path);
    if (!Files.isRegularFile(file)) throw new IllegalArgumentException("File not found: " + path);
    if (!comic(file) && !TitleMediaResolver.isVideo(file))
      throw new IllegalArgumentException("Choose a video or a .cbz comic");
    return file.toRealPath();
  }

  private static boolean comic(Path file) {
    return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cbz");
  }

  /** A new folder inside the repository, given as safe relative segments. */
  private static Path folder(Path root, String folder) {
    if (folder == null || folder.isBlank())
      throw new IllegalArgumentException("Choose a folder for the title");
    Path result = root;
    for (String segment : folder.strip().split("/")) {
      if (!FOLDER.matcher(segment).matches())
        throw new IllegalArgumentException("Folder names use letters, numbers, spaces, . _ and -");
      result = result.resolve(segment);
    }
    return result;
  }

  private static String language(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.strip();
  }

  /** A comic body listing the CBZ's pages in natural order, read right to left unless refused. */
  private ComicDocument comicBody(Path file, Create request) throws IOException {
    var entries = pages(file);
    var pages = new ArrayList<ComicPage>();
    for (int i = 0; i < entries.size(); i++)
      pages.add(
          new ComicPage("page-%03d".formatted(i + 1), entries.get(i), Integer.toString(i + 1)));
    var direction =
        Boolean.FALSE.equals(request.rightToLeft()) ? ReadingDirection.LTR : ReadingDirection.RTL;
    return new ComicDocument(null, direction, null, pages, List.of());
  }

  /** Image entries in natural order, so page-2 comes before page-10. */
  private List<String> pages(Path file) throws IOException {
    var pages = new ArrayList<String>();
    for (String entry : cbz.entries(file))
      if (IMAGES.stream().anyMatch(entry.toLowerCase(Locale.ROOT)::endsWith)) pages.add(entry);
    pages.sort(TitleImportService::natural);
    if (pages.isEmpty()) throw new IllegalArgumentException("This CBZ has no page images");
    return pages;
  }

  private static final Pattern NUMBER = Pattern.compile("\\d+|\\D+");

  private static int natural(String a, String b) {
    Matcher left = NUMBER.matcher(a), right = NUMBER.matcher(b);
    while (left.find() && right.find()) {
      String x = left.group(), y = right.group();
      int order =
          Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))
              ? Comparator.comparing((String s) -> s.length())
                  .thenComparing(Comparator.naturalOrder())
                  .compare(x.replaceFirst("^0+(?!$)", ""), y.replaceFirst("^0+(?!$)", ""))
              : x.compareTo(y);
      if (order != 0) return order;
    }
    return a.compareTo(b);
  }
}
