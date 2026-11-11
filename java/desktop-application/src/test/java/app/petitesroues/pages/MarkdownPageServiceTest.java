package app.petitesroues.pages;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.model.library.pages.MarkdownPageEntry;
import app.petitesroues.model.library.repository.Repository;
import app.petitesroues.model.pages.MarkdownPage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownPageServiceTest {
  @TempDir Path folder;

  @Test
  void listsMarkdownInTheNamedFolderOfEachRepositoryOnly() throws Exception {
    Path quizzes = Files.createDirectories(folder.resolve("french/quizzes"));
    Files.writeString(quizzes.resolve("verb-objects.md"), "Intro\n\n# Verb objects\n\nDrill…\n");
    Files.writeString(quizzes.resolve("common-irregular-verbs.md"), "No heading here.\n");
    Files.writeString(quizzes.resolve("notes.txt"), "not a quiz");
    Files.createDirectories(quizzes.resolve("drafts.md"));
    Path notes = Files.createDirectories(folder.resolve("french/notes"));
    Files.writeString(notes.resolve("imperative.md"), "# Imperative\n");
    Files.writeString(folder.resolve("french/README.md"), "# Not a page\n");
    Files.createDirectories(folder.resolve("films"));
    var store = mock(ContentRepositoryStore.class);
    when(store.list())
        .thenReturn(
            List.of(
                repository("french", folder.resolve("french")),
                repository("films", folder.resolve("films")),
                repository("gone", folder.resolve("gone"))));
    var service =
        new MarkdownPageService(store, new MarkdownPageScanner(), new MarkdownPageReader());

    assertEquals(
        List.of(
            entry(
                "quizzes/common-irregular-verbs.md",
                "common-irregular-verbs",
                "No heading here.\n"),
            entry(
                "quizzes/verb-objects.md", "Verb objects", "Intro\n\n# Verb objects\n\nDrill…\n")),
        service.pages(MarkdownPageService.QUIZZES));
    assertEquals(
        List.of(entry("notes/imperative.md", "Imperative", "# Imperative\n")),
        service.pages(MarkdownPageService.NOTES));
  }

  private static MarkdownPageEntry entry(String path, String title, String text) {
    return new MarkdownPageEntry(
        new Repository("french", "french"), path, new MarkdownPage(title, text));
  }

  private static ContentRepository repository(String name, Path path) {
    return new ContentRepository(name, name, path.toString(), null, 0, 0);
  }
}
