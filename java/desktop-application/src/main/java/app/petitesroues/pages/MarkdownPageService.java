package app.petitesroues.pages;

import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.model.library.MarkdownPageEntry;
import app.petitesroues.model.library.Repository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Lists the registered content repositories' general quizzes ({@code quizzes/}) and study notes
 * ({@code notes/}), read from their files on each request so a newly committed page appears without
 * a refresh. Repositories whose folder is missing are skipped; nothing is indexed or written.
 */
@Service
public class MarkdownPageService {
  /** The folder holding general quizzes: prompts for a chat app. */
  public static final String QUIZZES = MarkdownPageEntry.QUIZZES;

  /** The folder holding study notes: pages the learner reads in the library. */
  public static final String NOTES = MarkdownPageEntry.NOTES;

  private final ContentRepositoryStore repositories;
  private final MarkdownPageScanner scanner;
  private final MarkdownPageReader reader;

  public MarkdownPageService(
      ContentRepositoryStore repositories, MarkdownPageScanner scanner, MarkdownPageReader reader) {
    this.repositories = repositories;
    this.scanner = scanner;
    this.reader = reader;
  }

  /** Every page in the repositories' [folder], in repository order and then by file name. */
  public List<MarkdownPageEntry> pages(String folder) throws IOException {
    List<MarkdownPageEntry> pages = new ArrayList<>();
    for (var repository : repositories.list()) {
      Path root = Path.of(repository.localPath());
      if (!Files.isDirectory(root)) continue;
      var owner = new Repository(repository.id(), repository.name());
      for (Path file : scanner.scan(root, folder))
        pages.add(
            new MarkdownPageEntry(owner, folder + "/" + file.getFileName(), reader.read(file)));
    }
    return pages;
  }
}
