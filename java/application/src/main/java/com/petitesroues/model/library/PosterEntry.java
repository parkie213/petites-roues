package com.petitesroues.model.library;

import com.petitesroues.library.discovery.MediaKind;
import com.petitesroues.library.shelves.MediaNames;
import com.petitesroues.library.shelves.Shelvable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One title or media file as the Posters view shows it: a title whose media was found, or a video
 * or CBZ that no study document references yet, named from its path ({@link MediaNames}). The
 * fields a card renders and opens with; {@link com.petitesroues.library.shelves.Shelves} groups
 * these into cards.
 *
 * @param id the title's local ID, or {@code media:<folder>/<path>} for media without a document
 * @param documentId the document's portable ID; media without a document uses {@code id}
 * @param type {@code video} or {@code comic}
 * @param repositoryId the folder holding the document, or for media without one, the media
 * @param fingerprint set only for media without a document, which plays and is pictured by it
 * @param unavailable what needs fixing, in display order, as {@link CatalogueItem#unavailable}
 * @param progress how far through the title the local resume position is, or null
 * @param mediaPath the media's path within its folder
 */
public record PosterEntry(
    String id,
    String documentId,
    String title,
    String series,
    Integer season,
    Integer episode,
    Integer volume,
    String shelf,
    String sourceLanguage,
    String type,
    String repositoryId,
    Double durationSeconds,
    boolean documentAvailable,
    boolean videoAvailable,
    boolean mediaAvailable,
    boolean posterAvailable,
    boolean seriesPosterAvailable,
    Map<String, String> unavailable,
    Double progress,
    String fingerprint,
    String mediaPath)
    implements Shelvable {

  /** A title with a study document. */
  public static PosterEntry of(CatalogueItem title) {
    return new PosterEntry(
        title.id(),
        title.documentId(),
        title.title(),
        title.series(),
        title.season(),
        title.episode(),
        title.volume(),
        title.shelf(),
        title.sourceLanguage(),
        title.type(),
        title.repositoryId(),
        title.durationSeconds(),
        title.documentAvailable(),
        title.videoAvailable(),
        title.mediaAvailable(),
        title.posterAvailable(),
        title.seriesPosterAvailable(),
        title.unavailable(),
        title.progress(),
        null,
        title.mediaPath());
  }

  /** A video or CBZ that no study document references, named from its path. */
  public static PosterEntry of(MediaItem media) {
    var named = MediaNames.fromPath(media.relativePath());
    String id = "media:" + media.repositoryId() + "/" + media.relativePath();
    Map<String, String> unavailable = new LinkedHashMap<>();
    for (int i = 0; i < media.warnings().size(); i++)
      unavailable.put("media-" + i, media.warnings().get(i));
    return new PosterEntry(
        id,
        id,
        named.title(),
        named.series(),
        named.season(),
        named.episode(),
        null,
        null,
        null,
        media.kind(),
        media.repositoryId(),
        media.durationSeconds(),
        false,
        false,
        true,
        media.posterAvailable(),
        media.seriesPosterAvailable(),
        unavailable,
        null,
        media.fingerprint(),
        media.relativePath());
  }

  /**
   * Where this entry's card images are served: by title ID, or by fingerprint without a document.
   */
  public String cardUrl(boolean series) {
    String base =
        fingerprint != null ? "/api/media/" + encode(fingerprint) : "/api/titles/" + encode(id);
    return base + (series ? "/series-card" : "/card");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  @Override
  public MediaKind kind() {
    return "comic".equals(type) ? MediaKind.COMIC : MediaKind.VIDEO;
  }

  @Override
  public boolean hasTitleArt() {
    return posterAvailable || (kind() == MediaKind.COMIC && mediaAvailable);
  }

  @Override
  public boolean hasSeriesPoster() {
    return seriesPosterAvailable;
  }
}
