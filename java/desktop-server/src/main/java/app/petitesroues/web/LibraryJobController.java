package app.petitesroues.web;

import app.petitesroues.library.ContentRepositoryService;
import app.petitesroues.library.LibraryJobService;
import app.petitesroues.model.library.LibraryJob;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Add and Refresh as background jobs whose progress the library polls, so adding a large folder
 * shows how far it has got. The synchronous endpoints in {@link RepositoriesController} remain for
 * scripts and tests. Work runs in {@link LibraryJobService}; this controller starts jobs and
 * returns their latest state.
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
    return jobs.latest()
        .map(job -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(job))
        .orElse(ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
  }

  /** The latest snapshot of the retained job, including its terminal state. */
  @GetMapping("/{id}")
  public ResponseEntity<LibraryJob> job(@PathVariable String id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.get(id));
  }
}
