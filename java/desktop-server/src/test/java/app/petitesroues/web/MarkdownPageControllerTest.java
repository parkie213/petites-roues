package app.petitesroues.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.petitesroues.model.library.pages.MarkdownPageEntry;
import app.petitesroues.model.library.repository.Repository;
import app.petitesroues.model.pages.MarkdownPage;
import app.petitesroues.pages.MarkdownPageService;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownPageControllerTest {
  @Test
  void projectsLibraryContextIntoExistingHttpFields() throws Exception {
    var service = mock(MarkdownPageService.class);
    when(service.pages(MarkdownPageService.NOTES))
        .thenReturn(
            List.of(
                new MarkdownPageEntry(
                    new Repository("root-id", "French"),
                    "notes/verbs.md",
                    new MarkdownPage("Verbs", "# Verbs\n"))));
    var response = new MarkdownPageController(service).notes();
    assertEquals(
        List.of(
            new MarkdownPageController.PageResponse(
                "root-id", "French", "verbs", "Verbs", "# Verbs\n")),
        response.getBody());
  }
}
