package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * One title's study document: metadata, languages and exactly one body, a video or a comic.
 * Optional metadata ({@code series}, {@code shelf}, {@code sourceUrl}) is {@code null} when absent.
 * {@code shelf} names the library shelf the title is shown on (for example "Manga"); without it the
 * library chooses Comics, Series or Films from the body.
 *
 * <p>The body is never {@code null}, so a document cannot have both kinds of body or neither. In
 * JSON the body sits under its kind, {@code "video"} or {@code "comic"}, after the metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudyDocument(
    String id,
    String title,
    String sourceLanguage,
    String translationLanguage,
    String notesLanguage,
    String series,
    String shelf,
    String sourceUrl,
    @JsonIgnore StudyBody body) {
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

  /** Reads the JSON form, where the body is under {@code "video"} or {@code "comic"}. */
  @JsonCreator
  static StudyDocument fromJson(
      @JsonProperty("id") String id,
      @JsonProperty("title") String title,
      @JsonProperty("sourceLanguage") String sourceLanguage,
      @JsonProperty("translationLanguage") String translationLanguage,
      @JsonProperty("notesLanguage") String notesLanguage,
      @JsonProperty("series") String series,
      @JsonProperty("shelf") String shelf,
      @JsonProperty("sourceUrl") String sourceUrl,
      @JsonProperty("video") VideoDocument video,
      @JsonProperty("comic") ComicDocument comic) {
    if (video != null && comic != null)
      throw new IllegalArgumentException("Exactly one video or comic body is required");
    return new StudyDocument(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        shelf,
        sourceUrl,
        video != null ? video : comic);
  }

  /** Writes the body under its kind, the inverse of {@link #fromJson}. */
  @JsonAnyGetter
  Map<String, StudyBody> bodyByKind() {
    return Map.of(body instanceof ComicDocument ? "comic" : "video", body);
  }

  /**
   * The video body, for operations that only apply to videos.
   *
   * @throws IllegalArgumentException if this is a comic
   */
  @JsonIgnore
  public VideoDocument video() {
    if (body instanceof VideoDocument video) return video;
    throw new IllegalArgumentException("This operation requires a video document");
  }

  /**
   * The comic body, for operations that only apply to comics.
   *
   * @throws IllegalArgumentException if this is a video
   */
  @JsonIgnore
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
