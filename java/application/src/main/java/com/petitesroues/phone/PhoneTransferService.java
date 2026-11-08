package com.petitesroues.phone;

import com.petitesroues.documents.StudyDocumentCodec;
import com.petitesroues.library.LibraryService;
import com.petitesroues.library.TitleMediaService;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.library.persistence.ContentRepositoryStore;
import com.petitesroues.model.library.CatalogueEntry;
import com.petitesroues.model.library.ContentRepository;
import com.petitesroues.model.library.IndexedMedia;
import com.petitesroues.model.sync.DesktopTitle;
import com.petitesroues.model.sync.TitleFile;
import com.petitesroues.study.ReviewQueueService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
 */
@Service
public class PhoneTransferService {
  private final CatalogueStore catalogue;
  private final TitleMediaService media;
  private final ContentRepositoryStore repositories;
  private final ReviewQueueService served;
  private final StudyDocumentCodec codec = new StudyDocumentCodec();

  public PhoneTransferService(
      CatalogueStore catalogue,
      TitleMediaService media,
      ContentRepositoryStore repositories,
      ReviewQueueService served) {
    this.catalogue = catalogue;
    this.media = media;
    this.repositories = repositories;
    this.served = served;
  }

  /** A title as offered, and the desktop file behind each role. */
  private record Offer(DesktopTitle title, Map<String, Path> files) {}

  /** Every title a phone could copy, by series then number then title. */
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
    return titles;
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
    return codec.encode(served.document(entry)).getBytes(StandardCharsets.UTF_8);
  }

  private Offer offer(String id) throws IOException {
    var entry = catalogue.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title"));
    var offer = offer(entry, media.index(), names());
    if (offer == null || offer.title().problem() != null)
      throw new NoSuchElementException(
          offer == null ? "This title's document is missing" : offer.title().problem());
    return offer;
  }

  /** The offer for a title, or null when its document is not on disk. */
  private static Offer offer(
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
      String folder = names.getOrDefault(item.repositoryId(), item.repositoryId());
      files.put(TitleFile.MEDIA, item.file());
      list.add(
          new TitleFile(
              TitleFile.MEDIA,
              folder + "/" + item.relativePath(),
              Files.size(item.file()),
              item.fingerprint()));
      Path poster = LibraryService.poster(item);
      Path seriesPoster = LibraryService.seriesPoster(item);
      if (poster != null) add(files, list, TitleFile.POSTER, item, folder, poster);
      if (seriesPoster != null && !seriesPoster.equals(poster))
        add(files, list, TitleFile.SERIES_POSTER, item, folder, seriesPoster);
      list.add(
          new TitleFile(
              TitleFile.DOCUMENT,
              names.getOrDefault(location.repositoryId(), location.repositoryId())
                  + "/"
                  + entry.documentPath(),
              Files.size(entry.documentFile()),
              null));
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

  private static void add(
      Map<String, Path> files,
      List<TitleFile> list,
      String role,
      IndexedMedia item,
      String folder,
      Path image)
      throws IOException {
    String relative =
        Path.of(item.repositoryPath()).relativize(image).toString().replace('\\', '/');
    files.put(role, image);
    list.add(new TitleFile(role, folder + "/" + relative, Files.size(image), null));
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
