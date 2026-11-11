package app.petitesroues.web;

import app.petitesroues.library.LibraryService;
import app.petitesroues.library.LibraryUsageService;
import app.petitesroues.library.MediaLinkService;
import app.petitesroues.model.library.CatalogueItem;
import app.petitesroues.model.library.Library;
import app.petitesroues.model.library.LibraryUsage;
import app.petitesroues.model.library.MediaLink;
import app.petitesroues.model.library.Posters;
import java.io.IOException;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Typed library browsing. Subtitle editing is in StudyController. */
@RestController
@RequestMapping("/api")
public class LibraryController {
  private final LibraryService library;
  private final LibraryUsageService usage;
  private final MediaLinkService links;

  public LibraryController(
      LibraryService library, LibraryUsageService usage, MediaLinkService links) {
    this.library = library;
    this.usage = usage;
    this.links = links;
  }

  /** Registered repositories, each with its indexed title summaries. */
  @GetMapping("/library")
  public ResponseEntity<Library> library() throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.library());
  }

  /**
   * The Posters view's shelves and cards, optionally narrowed to one folder and one source
   * language.
   */
  @GetMapping("/library/posters")
  public ResponseEntity<Posters> posters(
      @RequestParam(required = false) String repository,
      @RequestParam(required = false) String language)
      throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(library.posters(repository, language));
  }

  /** Flat catalogue for title lookup, including individually registered documents. */
  @GetMapping("/library/titles")
  public ResponseEntity<List<CatalogueItem>> catalogue() throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.catalogue());
  }

  /** The library's disk usage and the free space left, measured now (it walks the folders). */
  @GetMapping("/library/usage")
  public ResponseEntity<LibraryUsage> usage() throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usage.usage());
  }

  /**
   * Links a media file to a title by hand: {@code {repositoryId, relativePath, titleId}}. It wins
   * over the fingerprint on both sides and survives Refresh; the document is not changed.
   */
  @PutMapping("/library/media-links")
  public ResponseEntity<Void> link(@RequestBody MediaLink link) {
    links.link(link);
    return ResponseEntity.noContent().build();
  }

  /** Removes a media file's manual link. */
  @DeleteMapping("/library/media-links")
  public ResponseEntity<Void> unlink(@RequestParam String repository, @RequestParam String path) {
    links.unlink(repository, path);
    return ResponseEntity.noContent().build();
  }
}
