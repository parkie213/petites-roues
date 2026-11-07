package com.petitesroues.web;

import com.petitesroues.library.ContentRepositoryService;
import com.petitesroues.library.LibraryJobService;
import com.petitesroues.model.library.LibraryJob;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Add and Refresh as background jobs whose progress streams to the library, so adding a large
 * folder shows how far it has got. The synchronous endpoints in {@link RepositoriesController}
 * remain for scripts and tests. Work runs in {@link LibraryJobService}; this controller starts jobs
 * and streams their state as server-sent events.
 */
@RestController
@RequestMapping("/api/jobs")
public class LibraryJobController {
  private final LibraryJobService jobs;
  private final ContentRepositoryService repositories;

  public LibraryJobController(LibraryJobService jobs, ContentRepositoryService repositories) {
    this.jobs = jobs;
    this.repositories = repositories;
  }

  /**
   * What to do: {@code add} a folder at {@code localPath} (optionally {@code name}d), or {@code
   * refresh} one folder by {@code repositoryId}, or every folder when it is absent.
   */
  public record Start(String kind, String localPath, String name, String repositoryId) {}

  /** Starts the job, or returns the one already running, with 202 Accepted. */
  @PostMapping
  public ResponseEntity<LibraryJob> start(@RequestBody Start request) {
    LibraryJob job =
        switch (request.kind() == null ? "" : request.kind()) {
          case "add" ->
              jobs.start(
                  "add",
                  progress ->
                      repositories.add(
                          new ContentRepositoryService.Add(request.localPath(), request.name()),
                          progress));
          case "refresh" ->
              jobs.start(
                  "refresh",
                  progress -> {
                    if (request.repositoryId() == null) repositories.refreshAll(progress);
                    else repositories.refresh(request.repositoryId(), progress);
                  });
          default -> throw new IllegalArgumentException("Unknown job: " + request.kind());
        };
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
  }

  /** The running job, or the last to finish, so a reloaded page can pick it up; 204 if none. */
  @GetMapping("/latest")
  public ResponseEntity<LibraryJob> latest() {
    return jobs.latest().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
  }

  /**
   * Streams the job's state as {@code job} events: now, after every change, and once finished, when
   * the stream ends.
   */
  @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(@PathVariable String id) {
    var emitter = new SseEmitter(0L);
    Runnable[] stop = {() -> {}};
    stop[0] =
        jobs.follow(
            id,
            job -> {
              try {
                emitter.send(SseEmitter.event().name("job").data(job, MediaType.APPLICATION_JSON));
                if (job.finished()) emitter.complete();
              } catch (IOException | IllegalStateException gone) {
                // The browser went away or the stream already ended; stop following.
                stop[0].run();
              }
            });
    emitter.onCompletion(() -> stop[0].run());
    emitter.onTimeout(() -> stop[0].run());
    emitter.onError(failure -> stop[0].run());
    return emitter;
  }
}
