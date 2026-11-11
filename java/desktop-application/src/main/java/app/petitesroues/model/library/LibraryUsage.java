package app.petitesroues.model.library;

import java.util.List;

/**
 * How much disk the library takes and how much is left, for the library header.
 *
 * @param libraryBytes total size of the registered repositories' library files (media, documents
 *     and images; not Git history or archived preparation runs)
 * @param disks free space on each disk holding a repository, in repository order
 */
public record LibraryUsage(long libraryBytes, List<Disk> disks) {
  public LibraryUsage {
    disks = List.copyOf(disks);
  }

  /**
   * One disk (file store) holding repositories.
   *
   * @param repositories names of the repositories on this disk
   * @param freeBytes space available to the app
   * @param totalBytes the disk's size
   */
  public record Disk(List<String> repositories, long freeBytes, long totalBytes) {
    public Disk {
      repositories = List.copyOf(repositories);
    }
  }
}
