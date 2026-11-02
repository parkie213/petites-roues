package com.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves optional media beside a given {@code document.json}: at most one supported video (any
 * filename) or CBZ, and an optional {@code poster.jpg|jpeg|png|webp} card image. A series poster is
 * the nearest poster in a folder above the title folder, below the repository root. Missing media
 * returns null; ambiguous media and media resolving outside its folder are rejected. Checks only
 * that title folder and never accesses the catalogue or scans repositories.
 *
 * <p>The title-folder contract is documented in {@code
 * docs/DOCUMENT-FORMAT.md#files-beside-the-document}.
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

  /** Returns the single supported sibling video, or null if none is present. */
  public static Path video(Path document) throws IOException {
    Path folder = document.getParent();
    if (!Files.isDirectory(folder)) return null;
    try (var siblings = Files.list(folder)) {
      var videos =
          siblings
              .filter(Files::isRegularFile)
              .filter(file -> VIDEO_EXTENSIONS.contains(extension(file)))
              .toList();
      if (videos.size() > 1)
        throw new IllegalArgumentException(
            "Multiple videos in title folder — keep one matching video");
      return videos.isEmpty() ? null : requireLocalFile(folder, videos.getFirst());
    }
  }

  /** Returns the single sibling CBZ; archive entries are validated separately by CbzReader. */
  public static Path comic(Path document) throws IOException {
    Path folder = document.getParent();
    if (!Files.isDirectory(folder)) return null;
    try (var siblings = Files.list(folder)) {
      var archives =
          siblings
              .filter(Files::isRegularFile)
              .filter(file -> extension(file).equals("cbz"))
              .toList();
      if (archives.size() > 1)
        throw new IllegalArgumentException("Multiple CBZ files in title folder — keep one edition");
      return archives.isEmpty() ? null : requireLocalFile(folder, archives.getFirst());
    }
  }

  private static Path requireLocalFile(Path folder, Path file) throws IOException {
    if (!file.toRealPath().getParent().equals(folder.toRealPath()))
      throw new IllegalArgumentException("Media must be inside its title folder");
    return file;
  }

  private static String extension(Path file) {
    String name = file.getFileName().toString();
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }
}
