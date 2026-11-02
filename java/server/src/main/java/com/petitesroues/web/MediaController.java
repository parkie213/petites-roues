package com.petitesroues.web;

import com.petitesroues.library.LibraryService;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Serves supplied files unchanged; Spring handles byte ranges and file streaming.
 *
 * <p>The video's URL carries its version (size and modification time): {@code /media} redirects to
 * {@code /media/{version}}. A browser keeps buffered video per URL, so when a title's video is
 * replaced, an open or reloaded player fetches the new file instead of mixing in cached data from
 * the old one.
 */
@RestController
@RequestMapping("/api/titles/{id}")
public class MediaController {
  private final LibraryService library;

  public MediaController(LibraryService library) {
    this.library = library;
  }

  @GetMapping("/media")
  public ResponseEntity<Void> currentVideo(@PathVariable String id) throws IOException {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create("media/" + version(video(id))))
        .cacheControl(CacheControl.noStore())
        .build();
  }

  /** Serves the current video; a stale version redirects to the current one. */
  @GetMapping("/media/{version}")
  public ResponseEntity<Resource> video(@PathVariable String id, @PathVariable String version)
      throws IOException {
    Path file = video(id);
    String current = version(file);
    if (!current.equals(version))
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create(current))
          .cacheControl(CacheControl.noStore())
          .build();
    Resource video = new FileSystemResource(file);
    return ResponseEntity.ok()
        .contentType(
            MediaTypeFactory.getMediaType(video).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .body(video);
  }

  private Path video(String id) throws IOException {
    return library
        .media(id)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "Video not downloaded — place one video in the title folder"));
  }

  private static String version(Path file) throws IOException {
    return Files.size(file) + "-" + Files.getLastModifiedTime(file).toMillis();
  }
}
