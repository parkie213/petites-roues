package com.petitesroues.library.discovery;

import java.util.Locale;
import java.util.Set;

/** The kinds of media the library indexes, recognised by file extension whatever its case. */
public enum MediaKind {
  VIDEO,
  COMIC;

  private static final Set<String> VIDEO_EXTENSIONS =
      Set.of("mp4", "mkv", "webm", "mov", "m4v", "avi", "ogv");

  /** The kind of media a file name names, or null when it is not media. */
  public static MediaKind of(String fileName) {
    String extension = extension(fileName);
    if (VIDEO_EXTENSIONS.contains(extension)) return VIDEO;
    return extension.equals("cbz") ? COMIC : null;
  }

  /** Whether a file may be a study document; the document reader decides whether it is one. */
  public static boolean documentCandidate(String fileName) {
    return extension(fileName).equals("json");
  }

  /** The kind's name as the index and the API store it: {@code video} or {@code comic}. */
  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  static String extension(String fileName) {
    return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }
}
