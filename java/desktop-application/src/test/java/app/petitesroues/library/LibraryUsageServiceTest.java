package app.petitesroues.library;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.library.discovery.LibraryUsageScanner;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.library.ContentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryUsageServiceTest {
  @TempDir Path folder;

  @Test
  void sumsRepositoriesGroupsThemByDiskAndSkipsMissingFolders() throws Exception {
    Files.createDirectories(folder.resolve("films"));
    Files.createDirectories(folder.resolve("manga"));
    Files.write(folder.resolve("films/a.mp4"), new byte[300]);
    Files.write(folder.resolve("manga/b.cbz"), new byte[50]);
    var store = mock(ContentRepositoryStore.class);
    when(store.list())
        .thenReturn(
            List.of(
                repository("films", folder.resolve("films")),
                repository("gone", folder.resolve("gone")),
                repository("manga", folder.resolve("manga"))));

    var usage = new LibraryUsageService(store, new LibraryUsageScanner()).usage();

    assertEquals(350, usage.libraryBytes());
    assertEquals(1, usage.disks().size(), "both folders are on the temp directory's disk");
    var disk = usage.disks().getFirst();
    assertEquals(List.of("films", "manga"), disk.repositories());
    assertTrue(disk.freeBytes() > 0 && disk.freeBytes() <= disk.totalBytes());
  }

  private static ContentRepository repository(String name, Path path) {
    return new ContentRepository(name, name, path.toString(), null, 0, 0);
  }
}
