package com.petitesroues.library.discovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Discovers what a registered folder offers the library, the same way on every platform: candidate
 * study documents (any {@code .json}), media (videos and {@code .cbz} comics) and each media file's
 * posters. One walk; it reads no file contents and never modifies anything. Hidden files and
 * folders, {@code node_modules} and {@code content-archive} (archived preparation runs) are
 * skipped.
 *
 * <p>Posters live with media. A folder holding exactly one media file is that file's <em>title
 * folder</em>; otherwise it is shared, like a series folder of episodes. A media file's own poster
 * is {@code <file name>.jpg|jpeg|png|webp} beside it, else {@code poster.*} in its title folder.
 * Its series poster is the nearest {@code poster.*} in a shared folder holding it or in a folder
 * above its title folder, up to and including the registered folder. A folder with several
 * candidate images has none, and the media it affects carries a {@link Media#posterProblem}.
 */
public final class ContentScanner {
  private static final Set<String> SKIPPED_FOLDERS = Set.of("content-archive", "node_modules");
  private static final Pattern IMAGE = Pattern.compile("(?i)(.+)\\.(jpe?g|png|webp)");

  private ContentScanner() {}

  /** A file found below the registered folder, at a path with forward slashes. */
  public record Found<F>(String relativePath, F handle) {}

  /**
   * A media file and its posters.
   *
   * @param poster its own card image, or null
   * @param seriesPoster the poster of the series it belongs to, or null
   * @param posterProblem why a poster could not be chosen (several candidates), or null
   */
  public record Media<F>(
      String relativePath,
      F handle,
      MediaKind kind,
      long size,
      long modifiedMillis,
      Found<F> poster,
      Found<F> seriesPoster,
      String posterProblem) {}

  /**
   * What one walk found, each list sorted by path.
   *
   * @param bytes the total size of every file seen, skipped folders excluded
   */
  public record Discovery<F>(List<Found<F>> documents, List<Media<F>> media, long bytes) {
    public Discovery {
      documents = List.copyOf(documents);
      media = List.copyOf(media);
    }
  }

  /** The image chosen in a folder: a file, nothing, or a problem when several qualify. */
  private record Choice<F>(Found<F> found, String problem) {
    static <F> Choice<F> none() {
      return new Choice<>(null, null);
    }

    boolean empty() {
      return found == null && problem == null;
    }
  }

  /**
   * Walks the registered folder once.
   *
   * @throws IOException if a folder cannot be listed
   */
  public static <F> Discovery<F> scan(ContentTree<F> tree) throws IOException {
    var walk = new Walk<F>(tree);
    walk.folder(tree.root(), "", Choice.none());
    walk.documents.sort(Comparator.comparing(Found::relativePath));
    walk.media.sort(Comparator.comparing(Media::relativePath));
    return new Discovery<>(walk.documents, walk.media, walk.bytes);
  }

  private static final class Walk<F> {
    final ContentTree<F> tree;
    final List<Found<F>> documents = new ArrayList<>();
    final List<Media<F>> media = new ArrayList<>();
    long bytes;

    Walk(ContentTree<F> tree) {
      this.tree = tree;
    }

    /** Lists one folder, records what it holds, then walks its subfolders. */
    void folder(F folder, String path, Choice<F> above) throws IOException {
      List<ContentTree.Entry<F>> files = new ArrayList<>(), folders = new ArrayList<>();
      for (var entry : tree.children(folder)) {
        if (entry.name().startsWith(".")) continue;
        if (entry.folder()) {
          if (!SKIPPED_FOLDERS.contains(entry.name())) folders.add(entry);
        } else {
          files.add(entry);
          bytes += entry.size();
        }
      }
      var here = files.stream().filter(file -> MediaKind.of(file.name()) != null).toList();
      boolean titleFolder = here.size() == 1;
      Choice<F> poster = image(files, path, "poster");
      for (var file : files)
        if (MediaKind.documentCandidate(file.name()))
          documents.add(new Found<>(join(path, file.name()), file.handle()));
      for (var file : here) {
        String name = file.name();
        Choice<F> own = image(files, path, name.substring(0, name.lastIndexOf('.')));
        if (own.empty() && titleFolder) own = poster;
        Choice<F> series = titleFolder || poster.empty() ? above : poster;
        media.add(
            new Media<>(
                join(path, name),
                file.handle(),
                MediaKind.of(name),
                file.size(),
                file.modifiedMillis(),
                own.found(),
                series.found(),
                own.problem() != null ? own.problem() : series.problem()));
      }
      Choice<F> below = poster.empty() ? above : poster;
      for (var child : folders) folder(child.handle(), join(path, child.name()), below);
    }

    /** The one image in {@code files} whose name before its extension is {@code base}. */
    private Choice<F> image(List<ContentTree.Entry<F>> files, String path, String base) {
      var matches =
          files.stream()
              .filter(
                  file -> {
                    var match = IMAGE.matcher(file.name());
                    return match.matches()
                        && match
                            .group(1)
                            .toLowerCase(Locale.ROOT)
                            .equals(base.toLowerCase(Locale.ROOT));
                  })
              .toList();
      if (matches.isEmpty()) return Choice.none();
      if (matches.size() > 1)
        return new Choice<>(
            null,
            "Several posters named "
                + base
                + " in "
                + (path.isEmpty() ? "the folder" : path)
                + " — keep one image");
      return new Choice<>(
          new Found<>(join(path, matches.get(0).name()), matches.get(0).handle()), null);
    }

    private static String join(String path, String name) {
      return path.isEmpty() ? name : path + "/" + name;
    }
  }
}
