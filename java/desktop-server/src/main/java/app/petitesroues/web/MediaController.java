package app.petitesroues.web;

import app.petitesroues.library.LibraryService;
import app.petitesroues.model.library.IndexedMedia;
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
 * Serves media files unchanged; Spring handles byte ranges and file streaming. A title's video is
 * served by title id; any indexed video, with or without a study document, by its fingerprint, so
 * it can be watched before it has one.
 *
 * <p>The video's URL carries its version (size and modification time): {@code .../media} redirects
 * to {@code .../media/{version}}. A browser keeps buffered video per URL, so when a video is
 * replaced, an open or reloaded player fetches the new file instead of mixing in cached data from
 * the old one.
 */
@RestController
@RequestMapping("/api")
public class MediaController {
  private final LibraryService library;

  public MediaController(LibraryService library) {
    this.library = library;
  }

  @GetMapping("/titles/{id}/media")
  public ResponseEntity<Void> currentVideo(@PathVariable String id) throws IOException {
    return redirect("media/" + version(video(id)));
  }

  /** Serves the title's current video; a stale version redirects to the current one. */
  @GetMapping("/titles/{id}/media/{version}")
  public ResponseEntity<Resource> video(@PathVariable String id, @PathVariable String version)
      throws IOException {
    return serve(video(id), version);
  }

  @GetMapping("/media/{fingerprint}/video")
  public ResponseEntity<Void> currentIndexedVideo(@PathVariable String fingerprint)
      throws IOException {
    return redirect("video/" + version(indexedVideo(fingerprint)));
  }

  /** Serves an indexed video by fingerprint; a stale version redirects to the current one. */
  @GetMapping("/media/{fingerprint}/video/{version}")
  public ResponseEntity<Resource> indexedVideo(
      @PathVariable String fingerprint, @PathVariable String version) throws IOException {
    return serve(indexedVideo(fingerprint), version);
  }

  private static <T> ResponseEntity<T> redirect(String location) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(location))
        .cacheControl(CacheControl.noStore())
        .build();
  }

  private static ResponseEntity<Resource> serve(Path file, String version) throws IOException {
    String current = version(file);
    if (!current.equals(version)) return redirect(current);
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
                    "Video not found — add the folder that holds it, then Refresh"));
  }

  private Path indexedVideo(String fingerprint) {
    return library
        .indexedMedia(fingerprint)
        .filter(item -> "video".equals(item.kind()))
        .map(IndexedMedia::file)
        .orElseThrow(() -> new NoSuchElementException("Video not found — Refresh the library"));
  }

  private static String version(Path file) throws IOException {
    return Files.size(file) + "-" + Files.getLastModifiedTime(file).toMillis();
  }
}
