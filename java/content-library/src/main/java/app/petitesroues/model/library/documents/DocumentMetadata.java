package app.petitesroues.model.library.documents;

import app.petitesroues.library.discovery.MediaKind;
import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoDocument;

/**
 * A study document's library metadata, without cues, comic regions, storage or learner state.
 * Indexed libraries can retain this snapshot without loading the full document while browsing. The
 * document ID identifies portable content; it is not a platform's registration/progress key.
 */
public record DocumentMetadata(
    String documentId,
    String title,
    String series,
    MediaKind kind,
    Integer season,
    Integer episode,
    Integer volume,
    String shelf,
    String sourceLanguage,
    String sourceUrl,
    String referenceFingerprint,
    Double durationSeconds) {
  /** Extracts library metadata from either document body without changing its content. */
  public static DocumentMetadata from(StudyDocument document) {
    if (document.body() instanceof VideoDocument video)
      return new DocumentMetadata(
          document.id(),
          document.title(),
          document.series(),
          MediaKind.VIDEO,
          video.season(),
          video.episode(),
          null,
          document.shelf(),
          document.sourceLanguage(),
          document.sourceUrl(),
          video.reference() == null ? null : video.reference().fingerprint(),
          video.reference() == null ? null : video.reference().durationSeconds());
    var comic = (ComicDocument) document.body();
    return new DocumentMetadata(
        document.id(),
        document.title(),
        document.series(),
        MediaKind.COMIC,
        null,
        null,
        comic.volume(),
        document.shelf(),
        document.sourceLanguage(),
        document.sourceUrl(),
        comic.reference() == null ? null : comic.reference().fingerprint(),
        null);
  }
}
