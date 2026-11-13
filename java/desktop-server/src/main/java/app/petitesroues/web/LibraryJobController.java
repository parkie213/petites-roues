package app.petitesroues.web;

import app.petitesroues.library.LibraryDiscoveryService;
import app.petitesroues.library.LibraryJobService;
import app.petitesroues.model.library.DiscoveryRequest;
import app.petitesroues.model.library.LibraryJob;
import org.springframework.http.CacheControl;
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
  private final LibraryDiscoveryService discovery;

  public LibraryJobController(LibraryJobService jobs, LibraryDiscoveryService discovery) {
    this.jobs = jobs;
    this.discovery = discovery;
  }

  /** Accepts the exact discovery command; a different in-flight operation returns 409. */
  @PostMapping
  public ResponseEntity<LibraryJob> start(@RequestBody DiscoveryRequest request) {
    return ResponseEntity.accepted().body(discovery.start(request));
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
