package app.petitesroues.library.shelves;

import app.petitesroues.library.discovery.MediaKind;

/**
 * What {@link Shelves} needs from a library entry: a title with a study document, or a media file
 * that no document describes yet (named by {@link MediaNames}). Each platform implements it on its
 * own entry type, so the grouping runs without server or Android types.
 */
public interface Shelvable {
  /** Whether the entry is a video or a comic. */
  MediaKind kind();

  /** Copies of one title share their document ID; media without a document has its own. */
  String documentId();

  String title();

  /** The series the document or path names; null or blank for none. */
  String series();

  Integer season();

  Integer episode();

  /** A comic's tome number. */
  Integer volume();

  /** The shelf the document names, such as "Manga"; null or blank for its kind's default. */
  String shelf();

  /** The document's source language as a BCP 47 tag; null for media without a document. */
  String sourceLanguage();

  boolean documentAvailable();

  /** Whether the video or CBZ is present, so the entry can open. */
  boolean mediaAvailable();

  /** Whether the entry has its own card image: a poster, else a comic's first page. */
  boolean hasTitleArt();

  /** Whether a poster sits in the folder above the media, standing for its whole series. */
  boolean hasSeriesPoster();

  /**
   * The linked media's path relative to its registered folder, or null. A comic whose document
   * names no series takes the one this path names.
   */
  String mediaPath();
}
