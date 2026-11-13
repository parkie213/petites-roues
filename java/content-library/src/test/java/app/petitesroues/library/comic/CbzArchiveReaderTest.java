package app.petitesroues.library.comic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Shared CBZ contracts, exercised through real seekable channels and malformed archives. */
class CbzArchiveReaderTest {
  @TempDir Path folder;

  private Path file() throws IOException {
    return Files.createTempFile(folder, "comic-", ".cbz");
  }

  private static void entry(ZipArchiveOutputStream zip, String name, byte[] bytes)
      throws IOException {
    zip.putArchiveEntry(new ZipArchiveEntry(name));
    zip.write(bytes);
    zip.closeArchiveEntry();
  }

  @Test
  void listsFilesAndNaturallyOrderedImagesAndReadsExactBytes() throws Exception {
    var file = file();
    byte[] image = {1, 2, 3};
    try (var zip = new ZipArchiveOutputStream(file)) {
      entry(zip, "page-10.PNG", image);
      entry(zip, "page-2.png", image);
      entry(zip, "notes.txt", new byte[0]);
      entry(zip, "folder.jpg/", new byte[0]);
    }
    var channel = Files.newByteChannel(file);
    try (var reader = new CbzArchiveReader(channel)) {
      assertEquals(Set.of("page-10.PNG", "page-2.png", "notes.txt"), reader.entries());
      assertEquals(List.of("page-2.png", "page-10.PNG"), reader.pages());
      assertArrayEquals(image, reader.read("page-2.png"));
      assertThrows(IllegalArgumentException.class, () -> reader.read("absent.png"));
      assertThrows(IllegalArgumentException.class, () -> reader.read("folder.jpg/"));
    }
    assertFalse(channel.isOpen(), "Closing the reader must close its supplied channel");
    try (var files = Files.list(folder)) {
      assertEquals(1, files.count(), "Reading must not extract files");
    }
  }

  @Test
  void rejectsDuplicateFileNamesAndClosesTheChannel() throws Exception {
    var file = file();
    try (var zip = new ZipArchiveOutputStream(file)) {
      entry(zip, "page.png", new byte[0]);
      entry(zip, "page.png", new byte[0]);
    }
    var channel = Files.newByteChannel(file);
    assertThrows(IllegalArgumentException.class, () -> new CbzArchiveReader(channel));
    assertFalse(channel.isOpen());
  }

  @Test
  void rejectsTooManyEntriesAndClosesTheChannel() throws Exception {
    var file = file();
    try (var zip = new ZipArchiveOutputStream(file)) {
      for (int i = 0; i < 20001; i++) entry(zip, i + ".txt", new byte[0]);
    }
    var channel = Files.newByteChannel(file);
    assertThrows(IllegalArgumentException.class, () -> new CbzArchiveReader(channel));
    assertFalse(channel.isOpen());
  }

  private Path oversizedPage() throws IOException {
    var file = file();
    try (var zip = new ZipArchiveOutputStream(file)) {
      zip.putArchiveEntry(new ZipArchiveEntry("huge.png"));
      var block = new byte[1024 * 1024];
      for (int i = 0; i < 33; i++) zip.write(block);
      zip.closeArchiveEntry();
    }
    return file;
  }

  private void rejectsOversizedPage(Path file) throws IOException {
    try (var reader = new CbzArchiveReader(Files.newByteChannel(file))) {
      var failure = assertThrows(IllegalArgumentException.class, () -> reader.read("huge.png"));
      assertEquals("CBZ page exceeds 32 MiB", failure.getMessage());
    }
  }

  @Test
  void rejectsDeclaredOversizedPages() throws Exception {
    rejectsOversizedPage(oversizedPage());
  }

  @Test
  void boundsActualDecompressionWhenTheZipUnderstatesPageSize() throws Exception {
    var file = oversizedPage();
    byte[] bytes = Files.readAllBytes(file);
    // The central directory claims one byte, while the deflate stream produces 33 MiB.
    int central = -1;
    for (int i = 0; i < bytes.length - 28; i++) {
      if (bytes[i] == 0x50 && bytes[i + 1] == 0x4b && bytes[i + 2] == 1 && bytes[i + 3] == 2) {
        central = i;
        break;
      }
    }
    assertTrue(central >= 0, "Fixture must have a central directory");
    bytes[central + 24] = 1;
    for (int i = 25; i <= 27; i++) bytes[central + i] = 0;
    Files.write(file, bytes);
    rejectsOversizedPage(file);
  }

  @Test
  void rejectsMalformedArchivesAndClosesTheChannel() throws Exception {
    var file = file();
    Files.writeString(file, "not a zip");
    var channel = Files.newByteChannel(file);
    assertThrows(IOException.class, () -> new CbzArchiveReader(channel));
    assertFalse(channel.isOpen());
  }
}
