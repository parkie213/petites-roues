package app.petitesroues.web;

import app.petitesroues.comic.ComicService;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Comic page images and local reading progress, independent of timed subtitle operations, and the
 * pages of a CBZ no study document references yet, by fingerprint.
 */
@RestController
@RequestMapping("/api")
public class ComicController {
  private final ComicService comics;

  public ComicController(ComicService comics) {
    this.comics = comics;
  }

  @GetMapping("/titles/{id}/comic/pages/{pageId}")
  public ResponseEntity<byte[]> image(@PathVariable String id, @PathVariable String pageId)
      throws IOException {
    return image(comics.image(id, pageId), CacheControl.noStore());
  }

  /** A CBZ's pages by entry name, in reading order, for reading it without a document. */
  @GetMapping("/media/{fingerprint}/pages")
  public ResponseEntity<List<String>> pages(@PathVariable String fingerprint) throws IOException {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comics.pages(fingerprint));
  }

  /**
   * Page {@code index} of {@link #pages}. The fingerprint names the file's content, so the browser
   * may keep a page for a day and turning back does not fetch it again.
   */
  @GetMapping("/media/{fingerprint}/pages/{index}")
  public ResponseEntity<byte[]> page(@PathVariable String fingerprint, @PathVariable int index)
      throws IOException {
    return image(comics.page(fingerprint, index), CacheControl.maxAge(Duration.ofDays(1)));
  }

  private static ResponseEntity<byte[]> image(ComicService.PageImage image, CacheControl cache) {
    return ResponseEntity.ok()
        .cacheControl(cache)
        .contentType(
            MediaTypeFactory.getMediaType(image.name()).orElse(MediaType.APPLICATION_OCTET_STREAM))
        .body(image.bytes());
  }
}
