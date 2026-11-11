package app.petitesroues.model.document;

import static app.petitesroues.model.document.Defaults.text;

import java.util.function.UnaryOperator;

/**
 * One title's study document: metadata, languages and exactly one body, a video or a comic.
 * Optional metadata ({@code series}, {@code shelf}, {@code sourceUrl}) is {@code null} when absent.
 * {@code shelf} names the library shelf the title is shown on (for example "Manga"); without it the
 * library chooses Comics, Series or Films from the body.
 *
 * <p>The body is never {@code null}, so a document cannot have both kinds of body or neither.
 */
public record StudyDocument(
    String id,
    String title,
    String sourceLanguage,
    String translationLanguage,
    String notesLanguage,
    String series,
    String shelf,
    String sourceUrl,
    StudyBody body) {
  public StudyDocument {
    shelf = shelf == null || shelf.isBlank() ? null : shelf.strip();
    id = text(id);
    title = text(title);
    sourceLanguage = text(sourceLanguage);
    translationLanguage = text(translationLanguage);
    notesLanguage = text(notesLanguage);
    if (body == null)
      throw new IllegalArgumentException("Exactly one video or comic body is required");
  }

  /** Creates a document without a chosen shelf. */
  public StudyDocument(
      String id,
      String title,
      String sourceLanguage,
      String translationLanguage,
      String notesLanguage,
      String series,
      String sourceUrl,
      StudyBody body) {
    this(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        null,
        sourceUrl,
        body);
  }

  /**
   * The video body, for operations that only apply to videos.
   *
   * @throws IllegalArgumentException if this is a comic
   */
  public VideoDocument video() {
    if (body instanceof VideoDocument video) return video;
    throw new IllegalArgumentException("This operation requires a video document");
  }

  /**
   * The comic body, for operations that only apply to comics.
   *
   * @throws IllegalArgumentException if this is a video
   */
  public ComicDocument comic() {
    if (body instanceof ComicDocument comic) return comic;
    throw new IllegalArgumentException("This operation requires a comic document");
  }

  public StudyDocument withId(String id) {
    return new StudyDocument(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        shelf,
        sourceUrl,
        body);
  }

  public StudyDocument withTitle(String title) {
    return new StudyDocument(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        shelf,
        sourceUrl,
        body);
  }

  public StudyDocument withLanguages(String source, String translation, String notes) {
    return new StudyDocument(id, title, source, translation, notes, series, shelf, sourceUrl, body);
  }

  public StudyDocument withBody(StudyBody body) {
    return new StudyDocument(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        shelf,
        sourceUrl,
        body);
  }

  /** Changes the video body; {@link IllegalArgumentException} if this is a comic. */
  public StudyDocument withVideo(UnaryOperator<VideoDocument> change) {
    return withBody(change.apply(video()));
  }

  /** Changes the comic body; {@link IllegalArgumentException} if this is a video. */
  public StudyDocument withComic(UnaryOperator<ComicDocument> change) {
    return withBody(change.apply(comic()));
  }
}
