package com.petitesroues.model.document;

import static com.petitesroues.model.document.Defaults.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.function.UnaryOperator;

/**
 * One title's study document: metadata, languages and exactly one {@code video} or {@code comic}
 * body. Optional metadata ({@code series}, {@code sourceUrl}, {@code edition}) is {@code null} when
 * absent.
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
    VideoDocument video,
    ComicDocument comic) {
  public StudyDocument {
    id = text(id);
    title = text(title);
    sourceLanguage = text(sourceLanguage);
    translationLanguage = text(translationLanguage);
    notesLanguage = text(notesLanguage);
  }

  public boolean hasVideo() {
    return video != null;
  }

  public boolean hasComic() {
    return comic != null;
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
        video,
        comic);
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
        video,
        comic);
  }

  public StudyDocument withLanguages(String source, String translation, String notes) {
    return new StudyDocument(
        id, title, source, translation, notes, series, sourceUrl, edition, video, comic);
  }

  public StudyDocument withVideo(VideoDocument video) {
    return new StudyDocument(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        sourceUrl,
        edition,
        video,
        comic);
  }

  public StudyDocument withVideo(UnaryOperator<VideoDocument> change) {
    return withVideo(change.apply(video));
  }

  public StudyDocument withComic(ComicDocument comic) {
    return new StudyDocument(
        id,
        title,
        sourceLanguage,
        translationLanguage,
        notesLanguage,
        series,
        sourceUrl,
        edition,
        video,
        comic);
  }

  public StudyDocument withComic(UnaryOperator<ComicDocument> change) {
    return withComic(change.apply(comic));
  }
}
