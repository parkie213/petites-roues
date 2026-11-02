package com.petitesroues.web;

import com.petitesroues.library.LibraryService;
import com.petitesroues.library.LibraryUsageService;
import com.petitesroues.model.library.CatalogueItem;
import com.petitesroues.model.library.Library;
import com.petitesroues.model.library.LibraryUsage;
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

  public LibraryController(LibraryService library, LibraryUsageService usage) {
    this.library = library;
    this.usage = usage;
  }

  /** Registered repositories, each with its indexed title summaries. */
  @GetMapping("/library")
  public ResponseEntity<Library> library() throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.library());
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
}
