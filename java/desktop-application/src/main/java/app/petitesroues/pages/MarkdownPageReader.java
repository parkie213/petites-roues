package app.petitesroues.pages;

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
  /** Reads page content without repository or registration context. */
  public MarkdownPage read(Path file) throws IOException {
    String name = file.getFileName().toString();
    String id = name.substring(0, name.length() - ".md".length());
    String text = Files.readString(file);
    return new MarkdownPage(MarkdownPage.title(text, id), text);
  }
}
