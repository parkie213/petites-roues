package app.petitesroues.comic;

import static app.petitesroues.support.SpecFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.document.ComicDocument;
import app.petitesroues.model.document.ComicPage;
import app.petitesroues.model.document.ReadingDirection;
import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.StudyDocumentValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComicDocumentTest {
  private static StudyDocument comic(List<ComicPage> pages) {
    return new StudyDocument(
        "book",
        "Book",
        "fr",
        "en",
        "en",
        null,
        null,
        new ComicDocument(null, ReadingDirection.RTL, null, pages, List.of()));
  }

  private static void invalid(List<ComicPage> pages, String message) {
    assertThrows(
        IllegalArgumentException.class,
        () -> StudyDocumentValidator.validate(comic(pages)),
        message);
  }

  @Test
  void validatesPageIdentityAndPaths() {
    assertDoesNotThrow(
        () -> StudyDocumentValidator.validate(comic(List.of(newPage("page", "001.jpg", null)))));
    for (String name :
        List.of(
            "../secret.jpg",
            "/secret.jpg",
            "a/../secret.jpg",
            "a\\secret.jpg",
            "a//b.jpg",
            "a.svg",
            "a/./b.jpg")) invalid(List.of(newPage("page", name, null)), name);
    invalid(List.of(newPage("same", "001.jpg", null), newPage("same", "002.jpg", null)), "id");
    invalid(
        List.of(newPage("first", "001.jpg", null), newPage("second", "001.jpg", null)), "entry");
  }

  @Test
  void boundsDecompressionWithoutExtractingArchivePaths(@TempDir Path folder) throws Exception {
    var file = folder.resolve("book.cbz");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry("huge.png"));
      var block = new byte[1024 * 1024];
      for (int i = 0; i < 33; i++) zip.write(block);
      zip.closeEntry();
    }
    var reader = new CbzReader();
    assertEquals(java.util.Set.of("huge.png"), reader.entries(file));
    assertThrows(IllegalArgumentException.class, () -> reader.read(file, "huge.png"));
    assertThrows(IllegalArgumentException.class, () -> reader.read(file, "absent.png"));
    try (var files = Files.list(folder)) {
      assertEquals(1, files.count());
    }
  }
}
