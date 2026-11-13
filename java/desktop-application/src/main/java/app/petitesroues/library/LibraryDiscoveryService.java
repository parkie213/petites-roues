package app.petitesroues.library;

import app.petitesroues.model.library.DiscoveryRequest;
import app.petitesroues.model.library.LibraryJob;
import app.petitesroues.model.library.RepositoryOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Owns discovery admission and per-repository outcomes; execution stays in LibraryJobService. */
@Service
public class LibraryDiscoveryService {
  private final LibraryJobService jobs;
  private final ContentRepositoryService repositories;

  public LibraryDiscoveryService(LibraryJobService jobs, ContentRepositoryService repositories) {
    this.jobs = jobs;
    this.repositories = repositories;
  }

  public LibraryJob start(DiscoveryRequest request) {
    if (request == null || !("add".equals(request.kind()) || "refresh".equals(request.kind())))
      throw new IllegalArgumentException("Choose add or refresh");
    var targets =
        "refresh".equals(request.kind())
            ? request.repositoryId() == null
                ? repositories.list().stream().map(repo -> repo.id()).toList()
                : List.of(request.repositoryId())
            : List.<String>of();
    return jobs.start(
        request.kind(),
        request,
        progress -> {
          if ("add".equals(request.kind())) {
            var added =
                repositories.add(
                    new ContentRepositoryService.Add(request.localPath(), request.name()),
                    progress);
            return List.of(new RepositoryOutcome(added.id(), "succeeded", added, null));
          }
          var outcomes = new ArrayList<RepositoryOutcome>();
          for (var id : targets) {
            try {
              outcomes.add(
                  new RepositoryOutcome(id, "succeeded", repositories.refresh(id, progress), null));
            } catch (NoSuchElementException removed) {
              outcomes.add(
                  new RepositoryOutcome(id, "skipped", null, "Repository no longer registered"));
            } catch (Exception failure) {
              if (Thread.currentThread().isInterrupted()) throw failure;
              outcomes.add(
                  new RepositoryOutcome(
                      id,
                      "failed",
                      null,
                      failure.getMessage() == null ? "Refresh failed" : failure.getMessage()));
            }
          }
          return outcomes;
        });
  }
}
