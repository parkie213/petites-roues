package com.petitesroues.library;

import com.petitesroues.comic.ComicService;
import com.petitesroues.library.discovery.TitleMediaResolver;
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
 * Chooses and caches the images on library cards. A title card shows the title folder's {@code
 * poster.*}, else a comic's first manifest page; a series card shows the nearest {@code poster.*}
 * above the title folder. Titles without one have no card image; the library draws a placeholder.
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
  private final Ffmpeg ffmpeg;
  private final Path cache;

  public CardArtService(
      CatalogueStore titles,
      LibraryService library,
      ComicService comics,
      Ffmpeg ffmpeg,
      @Value("${petitesroues.cards.cache:content/.cache/cards}") Path cache) {
    this.titles = titles;
    this.library = library;
    this.comics = comics;
    this.ffmpeg = ffmpeg;
    this.cache = cache;
  }

  /** The source of one card image and the version naming its cached JPEG. */
  public record Card(String titleId, String version, Path source, boolean comicCover) {}

  /**
   * The title card's source: its poster, else a comic's first page, else empty.
   *
   * @throws IllegalArgumentException if the title folder has several posters or CBZ files
   */
  public Optional<Card> titleCard(String id) throws IOException {
    var poster = library.poster(id);
    if (poster.isPresent())
      return Optional.of(card(id, poster.get(), false, List.of(poster.get())));
    var entry = titles.find(id);
    if (entry.isEmpty()
        || entry.get().location() == null
        || !"comic".equals(entry.get().location().documentType())) return Optional.empty();
    Path document = entry.get().documentFile();
    Path archive = TitleMediaResolver.comic(document);
    if (archive == null) return Optional.empty();
    // The first page can change with the document, so its version covers both files.
    return Optional.of(card(id, archive, true, List.of(archive, document)));
  }

  /** The series card's source: the nearest poster above the title folder, else empty. */
  public Optional<Card> seriesCard(String id) throws IOException {
    var poster = library.seriesPoster(id);
    return poster.map(file -> card(id, file, false, List.of(file)));
  }

  /**
   * Returns the card-sized JPEG for {@code card}, generating it with ffmpeg on first use.
   * Concurrent first requests may both generate it; each writes atomically.
   */
  public Path image(Card card) throws IOException {
    Path target = cache.resolve(card.version() + ".jpg");
    if (Files.isRegularFile(target)) return target;
    Files.createDirectories(cache);
    if (!card.comicCover()) {
      ffmpeg.thumbnail(card.source(), WIDTH, target);
      return target;
    }
    var document = comics.document(card.titleId());
    var first = document.comic().pages().getFirst();
    var page = comics.image(card.titleId(), first.id());
    Path extracted = Files.createTempFile(cache, card.version(), extension(page.name()));
    try {
      Files.write(extracted, page.bytes());
      ffmpeg.thumbnail(extracted, WIDTH, target);
      return target;
    } finally {
      Files.deleteIfExists(extracted);
    }
  }

  /**
   * Names a card by its versioned files, so titles sharing a series folder share one cached image
   * and any replaced file yields a new name.
   */
  private static Card card(String id, Path source, boolean comicCover, List<Path> versioned) {
    try {
      var stamp = new StringBuilder();
      for (Path file : versioned) {
        Path absolute = file.toAbsolutePath().normalize();
        stamp.append(absolute).append('\0').append(Files.size(absolute)).append('\0');
        stamp.append(Files.getLastModifiedTime(absolute).toMillis()).append('\0');
      }
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(stamp.toString().getBytes(StandardCharsets.UTF_8));
      return new Card(id, HexFormat.of().formatHex(digest).substring(0, 20), source, comicCover);
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
