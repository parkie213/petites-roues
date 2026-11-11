package app.petitesroues.model.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MarkdownPageTest {
  @Test
  void titleUsesFirstNonemptyLevelOneHeading() {
    assertEquals(
        "Passé composé",
        MarkdownPage.title("## Sub\r\n#   \r\n#  Passé composé \r\n# Later", "file"));
    assertEquals("file", MarkdownPage.title("#\n## Sub\nbody", "file"));
  }
}
