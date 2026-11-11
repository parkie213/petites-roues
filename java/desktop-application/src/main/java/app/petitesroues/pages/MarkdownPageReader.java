package app.petitesroues.pages;

import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.model.pages.MarkdownPage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Reads a Markdown page file. Only the first {@code # } heading is interpreted, as the page's name;
 * the rest of the text is passed on unchanged.
 */
@Component
public class MarkdownPageReader {
  /** The page in [file], which belongs to [repository]. */
  public MarkdownPage read(Path file, ContentRepository repository) throws IOException {
    String name = file.getFileName().toString();
    String id = name.substring(0, name.length() - ".md".length());
    String text = Files.readString(file);
    return new MarkdownPage(
        repository.id(), repository.name(), id, MarkdownPage.title(text, id), text);
  }
}
