package app.petitesroues.web;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.model.library.LibraryJob;
import app.petitesroues.support.HttpIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

class LibraryJobIntegrationTest extends HttpIntegrationTest {
  /** Starts a job and reads its event stream to the end, as the library page does. */
  private List<LibraryJob> run(Map<String, String> request) throws Exception {
    var started = http.post().uri("/api/jobs").body(request).retrieve().toEntity(LibraryJob.class);
    assertEquals(202, started.getStatusCode().value());
    return events(started.getBody().id());
  }

  private List<LibraryJob> events(String id) throws Exception {
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/jobs/" + id + "/events"))
                    .header("Accept", "text/event-stream")
                    .build(),
                HttpResponse.BodyHandlers.ofLines());
    assertEquals(200, response.statusCode());
    List<LibraryJob> jobs = new ArrayList<>();
    response
        .body()
        .filter(line -> line.startsWith("data:"))
        .forEach(line -> jobs.add(mapper.readValue(line.substring(5).strip(), LibraryJob.class)));
    return jobs;
  }

  @Test
  void addingAFolderStreamsItsProgressUntilItSucceeds() throws Exception {
    var jobs = run(Map.of("kind", "add", "localPath", root.toString()));

    var last = jobs.getLast();
    assertEquals("succeeded", last.state());
    assertTrue(jobs.subList(0, jobs.size() - 1).stream().noneMatch(LibraryJob::finished));
    assertTrue(
        jobs.stream()
            .anyMatch(
                job ->
                    job.progress() != null
                        && job.progress().phase().equals("media")
                        && "video.mp4".equals(job.progress().current())
                        && job.progress().total() == 1),
        "the media phase names the file being fingerprinted");
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
    assertEquals(List.of(last), events(last.id()));

    var refreshed = run(Map.of("kind", "refresh"));
    assertEquals("succeeded", refreshed.getLast().state());
  }

  @Test
  void aFailedJobSaysWhyAndUnknownJobsAreNotFound() throws Exception {
    var jobs = run(Map.of("kind", "add", "localPath", root.resolve("missing").toString()));
    assertEquals("failed", jobs.getLast().state());
    assertEquals("Repository directory not found", jobs.getLast().error());
    assertEquals(
        404,
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/jobs/nope/events"))
                    .build(),
                HttpResponse.BodyHandlers.discarding())
            .statusCode());
  }
}
