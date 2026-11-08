package com.petitesroues.model.sync;

import com.petitesroues.sync.FileFingerprints;
import java.util.List;

/**
 * One file a desktop title is made of, as the phone downloads it.
 *
 * @param role what the file is: {@link #DOCUMENT}, {@link #MEDIA}, {@link #POSTER}, {@link
 *     #SERIES_POSTER} or {@link #PAGE}
 * @param path where it goes on the phone, relative to the download folder, with {@code /} between
 *     segments: the desktop repository's name, then the file's path in that repository
 * @param bytes its size on the desktop; a document's is the size it is served at, pending edits
 *     replayed
 * @param fingerprint how to recognise the same file on the phone ({@link FileFingerprints}): the
 *     media's {@code v1:} fingerprint, {@code sha256:} of a document's or page's served bytes, or
 *     null for posters, which are compared by size
 * @param source the phone listener path it is served at, such as {@code /phone/titles/{id}/media}
 */
public record TitleFile(String role, String path, long bytes, String fingerprint, String source) {
  public static final String DOCUMENT = "document";
  public static final String MEDIA = "media";
  public static final String POSTER = "poster";
  public static final String SERIES_POSTER = "series-poster";

  /**
   * A study note or general quiz, a Markdown page from a repository's {@code notes/} or {@code
   * quizzes/}.
   */
  public static final String PAGE = "page";

  /**
   * Whether the file is generated or small, so it is fetched whole rather than resumed: a document
   * (served with pending edits) or a page.
   */
  public boolean whole() {
    return DOCUMENT.equals(role) || PAGE.equals(role);
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
