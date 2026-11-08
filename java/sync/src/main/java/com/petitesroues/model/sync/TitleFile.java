package com.petitesroues.model.sync;

import java.util.List;

/**
 * One file a desktop title is made of, as the phone downloads it.
 *
 * @param role what the file is to the title: {@link #DOCUMENT}, {@link #MEDIA}, {@link #POSTER} or
 *     {@link #SERIES_POSTER}; the desktop serves it at {@code PhoneRoutes.titleFile(id, role)}
 * @param path where it goes on the phone, relative to the download folder, with {@code /} between
 *     segments: the desktop repository's name, then the file's path in that repository
 * @param bytes its size on the desktop; for a document, the file's size before pending edits are
 *     replayed, so only an estimate
 * @param fingerprint the media's v1 fingerprint ({@code MediaFingerprint}), or null for documents
 *     and posters
 */
public record TitleFile(String role, String path, long bytes, String fingerprint) {
  public static final String DOCUMENT = "document";
  public static final String MEDIA = "media";
  public static final String POSTER = "poster";
  public static final String SERIES_POSTER = "series-poster";

  /** Whether this is the study document, which is always fetched whole, as currently served. */
  public boolean document() {
    return DOCUMENT.equals(role);
  }

  /**
   * The path's segments, refusing any that could leave the download folder.
   *
   * @throws IllegalArgumentException for an empty, absolute or {@code ..} path
   */
  public List<String> segments() {
    if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("\\"))
      throw new IllegalArgumentException("Unsafe download path: " + path);
    List<String> segments = List.of(path.split("/", -1));
    for (String segment : segments)
      if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
        throw new IllegalArgumentException("Unsafe download path: " + path);
    return segments;
  }
}
