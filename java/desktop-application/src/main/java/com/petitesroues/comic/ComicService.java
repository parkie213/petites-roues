package com.petitesroues.comic;

import com.petitesroues.library.LibraryService;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.model.document.ComicPage;
import com.petitesroues.model.document.StudyDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Coordinates comic loading and manifest-to-archive resolution for registered titles, and reading a
 * CBZ that no study document references yet, by its fingerprint, page by page in {@link
 * CbzReader#pages} order.
 */
@Service
public class ComicService {
  private final LibraryService library;
  private final CatalogueStore titles;
  private final CbzReader reader;

  public ComicService(LibraryService library, CatalogueStore titles, CbzReader reader) {
    this.library = library;
    this.titles = titles;
    this.reader = reader;
  }

  /** Requires a comic document; video titles cannot acquire comic reading progress. */
  public StudyDocument document(String id) throws IOException {
    var document = library.document(id);
    document.comic();
    return document;
  }

  /**
   * Validates the complete page manifest against one unambiguous local CBZ before reading starts.
   */
  public StudyDocument open(String id) throws IOException {
    var comic = document(id);
    var entries = reader.entries(archive(id));
    for (var page : comic.comic().pages())
      if (!entries.contains(page.entry()))
        throw new IllegalArgumentException("CBZ page is missing: " + page.entry());
    return comic;
  }

  /** Finds an immutable page identity in manifest order; it is not an archive path. */
  public ComicPage page(StudyDocument document, String pageId) {
    return document.comic().pages().stream()
        .filter(page -> page.id().equals(pageId))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Unknown comic page: " + pageId));
  }

  /** Resolves the requested page through the current document and reads its exact image entry. */
  public PageImage image(String id, String pageId) throws IOException {
    var page = page(document(id), pageId);
    return new PageImage(page.entry(), reader.read(archive(id), page.entry()));
  }

  /** The image entries of an indexed CBZ, in reading order, for reading it without a document. */
  public List<String> pages(String fingerprint) throws IOException {
    return reader.pages(cbz(fingerprint));
  }

  /** Page {@code index} (from zero) of {@link #pages}; only listed entries are ever read. */
  public PageImage page(String fingerprint, int index) throws IOException {
    Path archive = cbz(fingerprint);
    var pages = reader.pages(archive);
    if (index < 0 || index >= pages.size())
      throw new NoSuchElementException("No page " + index + " in this CBZ");
    return new PageImage(pages.get(index), reader.read(archive, pages.get(index)));
  }

  private Path cbz(String fingerprint) {
    return library
        .indexedMedia(fingerprint)
        .filter(item -> "comic".equals(item.kind()))
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "CBZ not found — add the folder that holds it, then Refresh"))
        .file();
  }

  private Path archive(String id) throws IOException {
    titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
    return library
        .media(id)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "CBZ not found — add the folder that holds it, then Refresh"));
  }

  /** The validated entry name identifies its MIME type; bytes never contain an extracted path. */
  public record PageImage(String name, byte[] bytes) {}
}
