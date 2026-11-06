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
 * Optional metadata ({@code series}, {@code sourceUrl}, {@code edition}) is {@code null} when
 * absent.
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
    String sourceUrl,
    String edition,
    @JsonIgnore StudyBody body) {
  public StudyDocument {
    id = text(id);
    title = text(title);
    sourceLanguage = text(sourceLanguage);
    translationLanguage = text(translationLanguage);
    notesLanguage = text(notesLanguage);
    if (body == null)
      throw new IllegalArgumentException("Exactly one video or comic body is required");
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
      @JsonProperty("sourceUrl") String sourceUrl,
      @JsonProperty("edition") String edition,
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
        sourceUrl,
        edition,
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
        sourceUrl,
        edition,
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
        sourceUrl,
        edition,
        body);
  }

  public StudyDocument withLanguages(String source, String translation, String notes) {
    return new StudyDocument(
        id, title, source, translation, notes, series, sourceUrl, edition, body);
  }

  public StudyDocument withBody(StudyBody body) {
    return new StudyDocument(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        sourceUrl,
        edition,
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
