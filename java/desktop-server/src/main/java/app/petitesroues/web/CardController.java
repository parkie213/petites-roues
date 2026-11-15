package app.petitesroues.web;

import app.petitesroues.library.CardArtService;
import app.petitesroues.library.CardArtService.Card;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Serves library card images, for titles by id and for media no document references by fingerprint.
 * Like {@link MediaController}, an unversioned URL redirects to the current version, and versioned
 * responses are cached by the browser indefinitely: a replaced poster or CBZ gets a new version and
 * therefore a new URL. Without a card image the response is 404 and the library draws a
 * placeholder.
 */
@RestController
@RequestMapping("/api")
public class CardController {
  private static final Logger LOG = LoggerFactory.getLogger(CardController.class);

  private final CardArtService cards;

  public CardController(CardArtService cards) {
    this.cards = cards;
  }

  /** The title card: its media's poster, else a comic's first page. */
  @GetMapping("/titles/{id}/card")
  public ResponseEntity<Void> titleCard(@PathVariable String id) throws IOException {
    return redirect("card/", require(cards.titleCard(id)));
  }

  @GetMapping("/titles/{id}/card/{version}")
  public ResponseEntity<Resource> titleCard(@PathVariable String id, @PathVariable String version)
      throws IOException {
    return serve(require(cards.titleCard(id)), version);
  }

  /** The series card: the series poster for the title's media. */
  @GetMapping("/titles/{id}/series-card")
  public ResponseEntity<Void> seriesCard(@PathVariable String id) throws IOException {
    return redirect("series-card/", require(cards.seriesCard(id)));
  }

  @GetMapping("/titles/{id}/series-card/{version}")
  public ResponseEntity<Resource> seriesCard(@PathVariable String id, @PathVariable String version)
      throws IOException {
    return serve(require(cards.seriesCard(id)), version);
  }

  /** A media file's card: its own poster, else a CBZ's first page. */
  @GetMapping("/media/{fingerprint}/card")
  public ResponseEntity<Void> mediaCard(@PathVariable String fingerprint) throws IOException {
    return redirect("card/", require(cards.mediaCard(fingerprint)));
  }

  @GetMapping("/media/{fingerprint}/card/{version}")
  public ResponseEntity<Resource> mediaCard(
      @PathVariable String fingerprint, @PathVariable String version) throws IOException {
    return serve(require(cards.mediaCard(fingerprint)), version);
  }

  /** A media file's series card. */
  @GetMapping("/media/{fingerprint}/series-card")
  public ResponseEntity<Void> mediaSeriesCard(@PathVariable String fingerprint) throws IOException {
    return redirect("series-card/", require(cards.mediaSeriesCard(fingerprint)));
  }

  @GetMapping("/media/{fingerprint}/series-card/{version}")
  public ResponseEntity<Resource> mediaSeriesCard(
      @PathVariable String fingerprint, @PathVariable String version) throws IOException {
    return serve(require(cards.mediaSeriesCard(fingerprint)), version);
  }

  /**
   * A landscape card: the title's poster, else a frame of its video. Without ffmpeg or a readable
   * video the frame cannot be made and the response is 404, so the library draws its placeholder.
   */
  @GetMapping("/titles/{id}/frame-card")
  public ResponseEntity<Void> titleFrameCard(@PathVariable String id) throws IOException {
    return redirect("frame-card/", require(cards.titleFrameCard(id)));
  }

  @GetMapping("/titles/{id}/frame-card/{version}")
  public ResponseEntity<Resource> titleFrameCard(
      @PathVariable String id, @PathVariable String version) throws IOException {
    return serve(require(cards.titleFrameCard(id)), version);
  }

  /** A media file's landscape card by fingerprint. */
  @GetMapping("/media/{fingerprint}/frame-card")
  public ResponseEntity<Void> mediaFrameCard(@PathVariable String fingerprint) throws IOException {
    return redirect("frame-card/", require(cards.mediaFrameCard(fingerprint)));
  }

  @GetMapping("/media/{fingerprint}/frame-card/{version}")
  public ResponseEntity<Resource> mediaFrameCard(
      @PathVariable String fingerprint, @PathVariable String version) throws IOException {
    return serve(require(cards.mediaFrameCard(fingerprint)), version);
  }

  private static Card require(Optional<Card> card) {
    return card.orElseThrow(() -> new NoSuchElementException("No card image"));
  }

  private static ResponseEntity<Void> redirect(String path, Card card) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(path + card.version()))
        .cacheControl(CacheControl.noStore())
        .build();
  }

  /** Serves the current image; a stale version redirects to the current one. */
  private ResponseEntity<Resource> serve(Card card, String version) throws IOException {
    if (!card.version().equals(version))
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create(card.version()))
          .cacheControl(CacheControl.noStore())
          .build();
    Path image;
    try {
      image = cards.image(card);
    } catch (IOException failure) {
      // A grabbed frame is optional art: without ffmpeg, or for an unreadable video, show the
      // placeholder instead of failing the page.
      if (!card.frame()) throw failure;
      LOG.warn("No frame for {}: {}", card.source(), failure.getMessage());
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable())
        .body(new FileSystemResource(image));
  }
}
