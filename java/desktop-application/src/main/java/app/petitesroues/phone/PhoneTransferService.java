package app.petitesroues.phone;

import app.petitesroues.documents.StudyDocumentCodec;
import app.petitesroues.library.LibraryService;
import app.petitesroues.library.TitleMediaService;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.model.library.IndexedMedia;
import app.petitesroues.model.sync.DesktopTitle;
import app.petitesroues.model.sync.TitleFile;
import app.petitesroues.pages.MarkdownPageScanner;
import app.petitesroues.pages.MarkdownPageService;
import app.petitesroues.study.ReviewQueueService;
import app.petitesroues.sync.FileFingerprints;
import app.petitesroues.sync.PhoneRoutes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * What paired phones can copy from this desktop: each catalogued title whose document is present
 * and whose media matches its reference fingerprint, with the files it needs (media, its posters,
 * the document). A phone fetches files by title and role, never by path, so only files the
 * catalogue lists can be read. Media matched only by a hand link is offered with a problem instead:
 * the phone links media by fingerprint, so it would not find it.
 *
 * <p>Where each file goes on the phone depends only on the title, never on where the desktop keeps
 * it, so moving media, renaming folders or registering them again changes nothing there. A title's
 * files are named by its ID, side by side in its series' folder, or at the top without a series:
 * {@code <id>.<media extension>}, its poster {@code <id>.<image extension>} and its document {@code
 * <id>.json}, with the series poster as {@code poster.*} in the series' folder. That is how the
 * library's scan pairs posters with media. Notes and quizzes keep a folder per repository, which is
 * how the phone groups them.
 */
@Service
public class PhoneTransferService {
  private final CatalogueStore catalogue;
  private final TitleMediaService media;
  private final ContentRepositoryStore repositories;
  private final ReviewQueueService served;
  private final MarkdownPageScanner pages;
  private final StudyDocumentCodec codec = new StudyDocumentCodec();

  public PhoneTransferService(
      CatalogueStore catalogue,
      TitleMediaService media,
      ContentRepositoryStore repositories,
      ReviewQueueService served,
      MarkdownPageScanner pages) {
    this.catalogue = catalogue;
    this.media = media;
    this.repositories = repositories;
    this.served = served;
    this.pages = pages;
  }

  /** A title as offered, and the desktop file behind each role. */
  private record Offer(DesktopTitle title, Map<String, Path> files) {}

  /**
   * The notes and quizzes entry when there are any pages, then every title a phone could copy, by
   * series then number then title.
   */
  public List<DesktopTitle> titles() throws IOException {
    var index = media.index();
    var names = names();
    List<DesktopTitle> titles = new ArrayList<>();
    for (var entry : catalogue.all()) {
      var offer = offer(entry, index, names);
      if (offer != null) titles.add(offer.title());
    }
    titles.sort(
        Comparator.comparing(
                (DesktopTitle title) -> title.series() == null ? title.title() : title.series(),
                String.CASE_INSENSITIVE_ORDER)
            .thenComparing(DesktopTitle::season, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(
                title -> title.volume() != null ? title.volume() : title.episode(),
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(DesktopTitle::title, String.CASE_INSENSITIVE_ORDER));
    var notes = pagesEntry(names);
    if (notes != null) titles.add(0, notes);
    return titles;
  }

  /** The repositories' notes and quizzes as one entry, or null when there are none. */
  private DesktopTitle pagesEntry(Map<String, String> names) throws IOException {
    List<TitleFile> files = new ArrayList<>();
    for (var repository : repositories.list()) {
      Path root = Path.of(repository.localPath());
      if (!Files.isDirectory(root)) continue;
      String folder = names.getOrDefault(repository.id(), repository.id());
      for (String kind : List.of(MarkdownPageService.NOTES, MarkdownPageService.QUIZZES))
        for (Path page : pages.scan(root, kind)) {
          byte[] bytes = Files.readAllBytes(page);
          String name = page.getFileName().toString();
          files.add(
              new TitleFile(
                  TitleFile.PAGE,
                  folder + "/" + kind + "/" + name,
                  bytes.length,
                  FileFingerprints.sha256(bytes),
                  PhoneRoutes.page(repository.id(), kind, name)));
        }
    }
    return files.isEmpty()
        ? null
        : new DesktopTitle(
            DesktopTitle.PAGES_ID,
            "Notes and quizzes",
            null,
            DesktopTitle.PAGES,
            null,
            null,
            null,
            null,
            List.copyOf(files));
  }

  /**
   * One of a repository's notes or quizzes, as listed in the notes and quizzes entry.
   *
   * @throws NoSuchElementException for anything else
   */
  public Path page(String repositoryId, String folder, String name) throws IOException {
    if (!folder.equals(MarkdownPageService.NOTES) && !folder.equals(MarkdownPageService.QUIZZES))
      throw new NoSuchElementException("No such page");
    var repository =
        repositories.list().stream()
            .filter(candidate -> candidate.id().equals(repositoryId))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No such page"));
    Path root = Path.of(repository.localPath());
    if (!Files.isDirectory(root)) throw new NoSuchElementException("No such page");
    return pages.scan(root, folder).stream()
        .filter(page -> page.getFileName().toString().equals(name))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("No such page"));
  }

  /**
   * The desktop file for one of a title's media or poster roles.
   *
   * @throws NoSuchElementException if the title cannot be copied or has no such file
   */
  public Path file(String id, String role) throws IOException {
    if (TitleFile.DOCUMENT.equals(role))
      throw new IllegalArgumentException("Fetch the document with document()");
    Path file = offer(id).files().get(role);
    if (file == null) throw new NoSuchElementException("This title has no " + role);
    return file;
  }

  /**
   * The title's document as the player sees it (pending edits replayed), in canonical JSON.
   *
   * @throws NoSuchElementException if the title cannot be copied
   */
  public byte[] document(String id) throws IOException {
    var entry = catalogue.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title"));
    offer(id);
    return served(entry);
  }

  private Offer offer(String id) throws IOException {
    var entry = catalogue.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title"));
    var offer = offer(entry, media.index(), names());
    if (offer == null || offer.title().problem() != null)
      throw new NoSuchElementException(
          offer == null ? "This title's document is missing" : offer.title().problem());
    return offer;
  }

  /** The document's bytes as the player sees it: pending edits replayed, canonical JSON. */
  private byte[] served(CatalogueEntry entry) throws IOException {
    return codec.encode(served.document(entry)).getBytes(StandardCharsets.UTF_8);
  }

  /** The offer for a title, or null when its document is not on disk. */
  private Offer offer(
      CatalogueEntry entry, TitleMediaService.Index index, Map<String, String> names)
      throws IOException {
    var location = entry.location();
    if (location == null || location.missing() || !Files.isRegularFile(entry.documentFile()))
      return null;
    boolean comic = "comic".equals(location.documentType());
    var matched = index.match(entry).filter(item -> Files.isRegularFile(item.file()));
    String problem =
        matched.isEmpty()
            ? "No " + (comic ? "CBZ" : "video") + " on the desktop matches its reference"
            : index.linkedByHand(entry, matched.get())
                ? "Its media is linked by hand on the desktop; the phone only links by fingerprint"
                : null;
    Map<String, Path> files = new LinkedHashMap<>();
    List<TitleFile> list = new ArrayList<>();
    if (problem == null) {
      IndexedMedia item = matched.get();
      String series = entry.series() == null ? null : segment(entry.series(), "series");
      String base = (series == null ? "" : series + "/") + segment(entry.id(), "title");
      files.put(TitleFile.MEDIA, item.file());
      list.add(
          new TitleFile(
              TitleFile.MEDIA,
              base + extension(item.file()),
              Files.size(item.file()),
              item.fingerprint(),
              PhoneRoutes.titleFile(entry.id(), TitleFile.MEDIA)));
      Path poster = LibraryService.poster(item);
      Path seriesPoster = LibraryService.seriesPoster(item);
      // A film's only poster is often its folder's, which the desktop counts as a series poster.
      if (series == null && poster == null) poster = seriesPoster;
      if (poster != null) add(files, list, entry, TitleFile.POSTER, base, poster);
      if (series != null && seriesPoster != null && !seriesPoster.equals(poster))
        add(files, list, entry, TitleFile.SERIES_POSTER, series + "/poster", seriesPoster);
      byte[] document = served(entry);
      list.add(
          new TitleFile(
              TitleFile.DOCUMENT,
              base + ".json",
              document.length,
              FileFingerprints.sha256(document),
              PhoneRoutes.titleFile(entry.id(), TitleFile.DOCUMENT)));
    }
    return new Offer(
        new DesktopTitle(
            entry.id(),
            entry.title(),
            entry.series(),
            comic ? "comic" : "video",
            entry.season(),
            entry.episode(),
            entry.volume(),
            problem,
            List.copyOf(list)),
        files);
  }

  /** Adds a poster, at {@code name} on the phone with the image's extension. */
  private static void add(
      Map<String, Path> files,
      List<TitleFile> list,
      CatalogueEntry entry,
      String role,
      String name,
      Path image)
      throws IOException {
    files.put(role, image);
    list.add(
        new TitleFile(
            role,
            name + extension(image),
            Files.size(image),
            null,
            PhoneRoutes.titleFile(entry.id(), role)));
  }

  /** The file's extension in lower case with its dot, or nothing. */
  private static String extension(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
  }

  /**
   * {@code name} made safe as one folder name on a phone: no separators or characters Android's
   * shared storage refuses, and not hidden (the library scan skips hidden folders); {@code
   * fallback} when nothing is left.
   */
  static String segment(String name, String fallback) {
    String safe = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").strip().replaceAll("^\\.+", "");
    return safe.isEmpty() ? fallback : safe;
  }

  /** Each repository's folder name on the phone: its name, made safe as one path segment. */
  private Map<String, String> names() {
    return repositories.list().stream()
        .collect(
            Collectors.toMap(
                ContentRepository::id,
                repository -> {
                  String name = repository.name().replaceAll("[/\\\\]", "-").strip();
                  return name.isEmpty() || name.equals(".") || name.equals("..")
                      ? repository.id()
                      : name;
                }));
  }
}
