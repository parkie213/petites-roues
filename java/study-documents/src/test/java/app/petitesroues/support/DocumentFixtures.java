package app.petitesroues.support;

import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.document.ComicPage;
import app.petitesroues.model.document.Line;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.document.VideoDocument;
import java.util.List;

/** Portable document fixtures with no catalogue or HTTP dependencies. */
public final class DocumentFixtures {
  private DocumentFixtures() {}

  /** A valid video document with one annotated cue, {@code stable-cue}, from 0.1 to 0.9 s. */
  public static StudyDocument document(String id) {
    var cue =
        new VideoCue(
            Line.of("stable-cue", "Bonjour !")
                .withTranslation("Hello!")
                .withNotes(List.of("A greeting.")),
            .1,
            .9,
            null);
    return new StudyDocument(
        id,
        "A generic title",
        "fr",
        "en",
        "en",
        null,
        null,
        new VideoDocument(null, null, null, List.of(cue)));
  }

  /** A French–English comic with the given pages and cues. */
  public static StudyDocument comic(
      String id, String title, ReadingDirection direction, List<ComicPage> pages) {
    return new StudyDocument(
        id,
        title,
        "fr",
        "en",
        "en",
        null,
        null,
        new ComicDocument(null, direction, null, pages, List.of()));
  }
}
