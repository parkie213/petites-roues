package com.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves the optional card images beside a study document: its own {@code
 * poster.jpg|jpeg|png|webp}, and a series poster, the nearest poster in a folder above the title
 * folder, below the repository root. Also says which files are videos. Media is not resolved here:
 * it is linked to documents by fingerprint ({@code TitleMediaService}). Checks only the folders
 * named and never accesses the catalogue or scans repositories.
 */
public final class TitleMediaResolver {
  private static final Set<String> VIDEO_EXTENSIONS =
      Set.of("mp4", "mkv", "webm", "mov", "m4v", "avi", "ogv");

  private static final Pattern POSTER =
      Pattern.compile("poster\\.(jpe?g|png|webp)", Pattern.CASE_INSENSITIVE);

  private TitleMediaResolver() {}

  /** Returns the title's own card image beside the document, or null if none is present. */
  public static Path poster(Path document) throws IOException {
    return folderPoster(document.getParent());
  }

  /**
   * Returns the nearest poster in a folder above the title folder, stopping before {@code
   * repositoryRoot} (a poster in the root never represents one series), or null.
   */
  public static Path seriesPoster(Path document, Path repositoryRoot) throws IOException {
    Path root = repositoryRoot.toAbsolutePath().normalize();
    for (Path folder = document.toAbsolutePath().normalize().getParent().getParent();
        folder != null && folder.startsWith(root) && !folder.equals(root);
        folder = folder.getParent()) {
      Path poster = folderPoster(folder);
      if (poster != null) return poster;
    }
    return null;
  }

  private static Path folderPoster(Path folder) throws IOException {
    if (!Files.isDirectory(folder)) return null;
    try (var siblings = Files.list(folder)) {
      var posters =
          siblings
              .filter(Files::isRegularFile)
              .filter(file -> POSTER.matcher(file.getFileName().toString()).matches())
              .toList();
      if (posters.size() > 1)
        throw new IllegalArgumentException(
            "Multiple posters in " + folder.getFileName() + " — keep one poster image");
      return posters.isEmpty() ? null : requireLocalFile(folder, posters.getFirst());
    }
  }

  /** Whether the file has an extension the library recognises as a video. */
  public static boolean isVideo(Path file) {
    return VIDEO_EXTENSIONS.contains(extension(file));
  }

  private static Path requireLocalFile(Path folder, Path file) throws IOException {
    if (!file.toRealPath().getParent().equals(folder.toRealPath()))
      throw new IllegalArgumentException("Poster must be inside its folder");
    return file;
  }

  private static String extension(Path file) {
    String name = file.getFileName().toString();
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }
}
