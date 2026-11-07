package com.petitesroues.library.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves the optional card images that live with a media file, so posters stay beside the videos
 * and comics they describe rather than in study-document repositories. Also says which files are
 * videos and comics.
 *
 * <p>A media file's folder is its <em>title folder</em> when it holds no other video or comic (a
 * film or tome in its own folder); otherwise the folder is shared, like a series folder of
 * episodes. A title's own poster is {@code <file name>.jpg|jpeg|png|webp} beside it, or {@code
 * poster.*} in its title folder. A series poster is the nearest {@code poster.*} in a shared folder
 * holding the file, or in a folder above its title folder, up to and including the registered
 * folder. Checks only the folders named; never accesses the catalogue.
 */
public final class TitleMediaResolver {
  private static final Set<String> VIDEO_EXTENSIONS =
      Set.of("mp4", "mkv", "webm", "mov", "m4v", "avi", "ogv");

  private static final String IMAGE_EXTENSION = "\\.(jpe?g|png|webp)";

  private TitleMediaResolver() {}

  /**
   * Returns the media file's own card image, or null if none is present.
   *
   * @throws IllegalArgumentException if a folder offers several candidates
   */
  public static Path poster(Path media) throws IOException {
    Path folder = media.toAbsolutePath().normalize().getParent();
    String name = media.getFileName().toString();
    Path own = image(folder, Pattern.quote(name.substring(0, name.lastIndexOf('.'))));
    if (own != null) return own;
    return titleFolder(folder) ? image(folder, "poster") : null;
  }

  /**
   * Returns the nearest series poster for the media file within {@code repositoryRoot}, or null.
   *
   * @throws IllegalArgumentException if a folder has several posters
   */
  public static Path seriesPoster(Path media, Path repositoryRoot) throws IOException {
    Path root = repositoryRoot.toAbsolutePath().normalize();
    Path folder = media.toAbsolutePath().normalize().getParent();
    if (titleFolder(folder)) folder = folder.getParent();
    for (; folder != null && folder.startsWith(root); folder = folder.getParent()) {
      Path poster = image(folder, "poster");
      if (poster != null) return poster;
    }
    return null;
  }

  /** Whether the folder holds exactly one video or comic. */
  private static boolean titleFolder(Path folder) throws IOException {
    if (!Files.isDirectory(folder)) return false;
    try (var files = Files.list(folder)) {
      return files.filter(Files::isRegularFile).filter(TitleMediaResolver::isMedia).limit(2).count()
          == 1;
    }
  }

  /** The one image named {@code base} plus an image extension in {@code folder}, or null. */
  private static Path image(Path folder, String base) throws IOException {
    if (folder == null || !Files.isDirectory(folder)) return null;
    var pattern = Pattern.compile("(?i)" + base + IMAGE_EXTENSION);
    try (var siblings = Files.list(folder)) {
      var images =
          siblings
              .filter(Files::isRegularFile)
              .filter(file -> pattern.matcher(file.getFileName().toString()).matches())
              .toList();
      if (images.size() > 1)
        throw new IllegalArgumentException(
            "Several posters in " + folder.getFileName() + " — keep one image");
      return images.isEmpty() ? null : requireLocalFile(folder, images.getFirst());
    }
  }

  /** Whether the file is a video or a comic the library indexes. */
  public static boolean isMedia(Path file) {
    return isVideo(file) || "cbz".equals(extension(file));
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
