package com.petitesroues.library;

import com.petitesroues.comic.CbzReader;
import com.petitesroues.comic.ComicService;
import com.petitesroues.library.persistence.CatalogueStore;
import com.petitesroues.media.Ffmpeg;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Chooses and caches the images on library cards. Card images live with media, not with study
 * documents (found by Refresh, see {@link com.petitesroues.library.discovery.ContentScanner}): a
 * title card shows its linked media's own poster, else a comic's first manifest page; a series card
 * shows the series poster for that media. A media file no document references has the same cards,
 * keyed by its fingerprint, with a CBZ's first page as its cover. Without one, the library draws a
 * placeholder.
 *
 * <p>Images are downscaled once to card size and kept in a local cache directory, never in content
 * folders. A card's version is derived from its source files' paths, sizes and modification times,
 * so replacing a poster or CBZ produces a new version and a new cached file.
 */
@Service
public class CardArtService {
  /** Card width in pixels; about twice the displayed width, for high-density screens. */
  private static final int WIDTH = 480;

  private final CatalogueStore titles;
  private final LibraryService library;
  private final ComicService comics;
  private final CbzReader archives;
  private final Ffmpeg ffmpeg;
  private final Path cache;

  public CardArtService(
      CatalogueStore titles,
      LibraryService library,
      ComicService comics,
      CbzReader archives,
      Ffmpeg ffmpeg,
      @Value("${petitesroues.cards.cache:content/.cache/cards}") Path cache) {
    this.titles = titles;
    this.library = library;
    this.comics = comics;
    this.archives = archives;
    this.ffmpeg = ffmpeg;
    this.cache = cache;
  }

  /**
   * The source of one card image and the version naming its cached JPEG.
   *
   * @param source an image, or a CBZ when {@code page} names the entry to show
   * @param page the CBZ entry holding the image, or null
   */
  public record Card(String version, Path source, String page) {}

  /**
   * The title card's source: its poster, else a comic's first page, else empty.
   *
   * @throws IllegalArgumentException if a folder has several posters
   */
  public Optional<Card> titleCard(String id) throws IOException {
    var poster = library.poster(id);
    if (poster.isPresent()) return Optional.of(card(poster.get(), null, List.of(poster.get())));
    var entry = titles.find(id);
    if (entry.isEmpty()
        || entry.get().location() == null
        || !"comic".equals(entry.get().location().documentType())) return Optional.empty();
    Path document = entry.get().documentFile();
    var archive = library.media(id).orElse(null);
    if (archive == null) return Optional.empty();
    String first = comics.document(id).comic().pages().getFirst().entry();
    // The first page can change with the document, so its version covers both files.
    return Optional.of(card(archive, first, List.of(archive, document)));
  }

  /** The series card's source: the series poster for the title's media, else empty. */
  public Optional<Card> seriesCard(String id) throws IOException {
    return library.seriesPoster(id).map(file -> card(file, null, List.of(file)));
  }

  /**
   * The card of a media file by fingerprint: its own poster, else a CBZ's first page ({@link
   * CbzReader#pages}), else empty.
   */
  public Optional<Card> mediaCard(String fingerprint) throws IOException {
    var item = library.indexedMedia(fingerprint);
    if (item.isEmpty()) return Optional.empty();
    Path poster = LibraryService.poster(item.get());
    if (poster != null) return Optional.of(card(poster, null, List.of(poster)));
    if (!"comic".equals(item.get().kind())) return Optional.empty();
    Path archive = item.get().file();
    return archives.pages(archive).stream()
        .findFirst()
        .map(page -> card(archive, page, List.of(archive)));
  }

  /** The series card of a media file by fingerprint, or empty. */
  public Optional<Card> mediaSeriesCard(String fingerprint) throws IOException {
    var item = library.indexedMedia(fingerprint);
    if (item.isEmpty()) return Optional.empty();
    return Optional.ofNullable(LibraryService.seriesPoster(item.get()))
        .map(file -> card(file, null, List.of(file)));
  }

  /**
   * Returns the card-sized JPEG for {@code card}, generating it with ffmpeg on first use.
   * Concurrent first requests may both generate it; each writes atomically.
   */
  public Path image(Card card) throws IOException {
    Path target = cache.resolve(card.version() + ".jpg");
    if (Files.isRegularFile(target)) return target;
    Files.createDirectories(cache);
    if (card.page() == null) {
      ffmpeg.thumbnail(card.source(), WIDTH, target);
      return target;
    }
    Path extracted = Files.createTempFile(cache, card.version(), extension(card.page()));
    try {
      Files.write(extracted, archives.read(card.source(), card.page()));
      ffmpeg.thumbnail(extracted, WIDTH, target);
      return target;
    } finally {
      Files.deleteIfExists(extracted);
    }
  }

  /**
   * Names a card by its versioned files and page, so cards sharing a poster share one cached image
   * and any replaced file yields a new name.
   */
  private static Card card(Path source, String page, List<Path> versioned) {
    try {
      var stamp = new StringBuilder(page == null ? "" : page).append('\0');
      for (Path file : versioned) {
        Path absolute = file.toAbsolutePath().normalize();
        stamp.append(absolute).append('\0').append(Files.size(absolute)).append('\0');
        stamp.append(Files.getLastModifiedTime(absolute).toMillis()).append('\0');
      }
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(stamp.toString().getBytes(StandardCharsets.UTF_8));
      return new Card(HexFormat.of().formatHex(digest).substring(0, 20), source, page);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static String extension(String entry) {
    return entry.substring(entry.lastIndexOf('.')).toLowerCase(Locale.ROOT);
  }
}
