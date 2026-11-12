package app.petitesroues.web;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.model.library.LibraryJob;
import app.petitesroues.support.HttpIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;

class LibraryJobIntegrationTest extends HttpIntegrationTest {
  /** Submits work, polls its terminal state and verifies the retained identity. */
  private LibraryJob run(Map<String, String> request) {
    var started = http.post().uri("/api/jobs").body(request).retrieve().toEntity(LibraryJob.class);
    assertEquals(202, started.getStatusCode().value());
    String id = started.getBody().id();
    await().atMost(Duration.ofSeconds(20)).until(() -> job(id).finished());
    return job(id);
  }

  private LibraryJob job(String id) {
    var response = http.get().uri("/api/jobs/" + id).retrieve().toEntity(LibraryJob.class);
    assertEquals("no-store", response.getHeaders().getCacheControl());
    return response.getBody();
  }

  @Test
  void addingAFolderCanBePolledUntilItSucceeds() throws Exception {
    var last = run(Map.of("kind", "add", "localPath", root.toString()));
    assertEquals("succeeded", last.state());
    assertNotNull(last.progress());
    var folders =
        http.get()
            .uri("/api/repositories")
            .retrieve()
            .body(new ParameterizedTypeReference<List<ContentRepository>>() {});
    assertEquals(1, folders.size());
    assertEquals(1, folders.getFirst().media());

    // A reloaded page finds the finished job, and following it again just reports the end.
    assertEquals(
        last.id(), http.get().uri("/api/jobs/latest").retrieve().body(LibraryJob.class).id());
    assertEquals(last, job(last.id()));

    var refreshed = run(Map.of("kind", "refresh"));
    assertEquals("succeeded", refreshed.state());
  }

  @Test
  void aFailedJobSaysWhyAndUnknownJobsAreNotFound() throws Exception {
    var job = run(Map.of("kind", "add", "localPath", root.resolve("missing").toString()));
    assertEquals("failed", job.state());
    assertEquals("Repository directory not found", job.error());
    assertThrows(
        HttpClientErrorException.NotFound.class,
        () -> http.get().uri("/api/jobs/nope").retrieve().toBodilessEntity());
  }
}
