package com.petitesroues.comic;

import com.petitesroues.library.LibraryService;
import com.petitesroues.library.discovery.TitleMediaResolver;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.model.document.ComicPage;
import com.petitesroues.model.document.StudyDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Coordinates comic loading and manifest-to-archive resolution for registered titles only. */
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
    if (document.hasComic()) return document;
    throw new IllegalArgumentException("This operation requires a comic document");
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

  private Path archive(String id) throws IOException {
    var title =
        titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
    var archive = TitleMediaResolver.comic(title.documentFile());
    if (archive == null)
      throw new NoSuchElementException("CBZ not downloaded — place one CBZ in the title folder");
    return archive;
  }

  /** The validated entry name identifies its MIME type; bytes never contain an extracted path. */
  public record PageImage(String name, byte[] bytes) {}
}
