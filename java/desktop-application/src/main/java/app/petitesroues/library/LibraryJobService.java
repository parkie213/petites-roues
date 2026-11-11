package app.petitesroues.library;

import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.model.library.LibraryJob;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Runs Add and Refresh in the background so the library can show their progress instead of waiting
 * on one long request. One job runs at a time: starting another while one runs returns the running
 * job, which the caller can follow. Jobs hold the {@link LibraryUpdateService} monitor while they
 * work, as the synchronous endpoints do, so edits never interleave with a refresh.
 *
 * <p>Only the latest job is remembered, in memory, so a reloaded page can pick it up again. The
 * work itself belongs to {@link ContentRepositoryService}; this service owns scheduling, state and
 * notifying followers.
 */
@Service
public class LibraryJobService {
  private static final System.Logger LOG = System.getLogger(LibraryJobService.class.getName());

  /** The work a job does, reporting progress as it goes. */
  @FunctionalInterface
  public interface Work {
    void run(LibraryProgress.Listener progress) throws Exception;
  }

  private final LibraryUpdateService updates;
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          task -> {
            var thread = new Thread(task, "library-job");
            thread.setDaemon(true);
            return thread;
          });
  private Job latest;

  public LibraryJobService(LibraryUpdateService updates) {
    this.updates = updates;
  }

  /** Starts {@code work} in the background, or returns the job already running. */
  public synchronized LibraryJob start(String kind, Work work) {
    if (latest != null && !latest.snapshot().finished()) return latest.snapshot();
    var job = new Job(UUID.randomUUID().toString(), kind);
    latest = job;
    worker.execute(() -> run(job, work));
    return job.snapshot();
  }

  /** The running job, or else the last one to finish; empty before any job has run. */
  public synchronized Optional<LibraryJob> latest() {
    return Optional.ofNullable(latest).map(Job::snapshot);
  }

  /**
   * Calls {@code follower} with the job's state now and after every change until it finishes. A
   * finished job is reported once. Returns a handle that stops the calls.
   *
   * @throws NoSuchElementException if the job is not the latest one
   */
  public synchronized Runnable follow(String id, Consumer<LibraryJob> follower) {
    if (latest == null || !latest.id.equals(id))
      throw new NoSuchElementException("No such job: " + id);
    return latest.follow(follower);
  }

  private void run(Job job, Work work) {
    try {
      synchronized (updates) {
        work.run(job::progress);
      }
      job.finish("succeeded", null);
    } catch (IllegalArgumentException | NoSuchElementException expected) {
      job.finish("failed", expected.getMessage());
    } catch (Exception unexpected) {
      LOG.log(System.Logger.Level.ERROR, "Library job failed", unexpected);
      job.finish("failed", "Something went wrong; the server log has the details");
    }
  }

  /** One job's changing state and the followers to tell about it. */
  private static final class Job {
    final String id;
    final String kind;
    final List<Consumer<LibraryJob>> followers = new CopyOnWriteArrayList<>();
    private LibraryJob state;

    Job(String id, String kind) {
      this.id = id;
      this.kind = kind;
      this.state = new LibraryJob(id, kind, "running", null, null);
    }

    synchronized LibraryJob snapshot() {
      return state;
    }

    void progress(LibraryProgress progress) {
      LibraryJob next;
      synchronized (this) {
        next = state = new LibraryJob(id, kind, "running", progress, null);
      }
      followers.forEach(follower -> follower.accept(next));
    }

    void finish(String outcome, String error) {
      LibraryJob next;
      synchronized (this) {
        next = state = new LibraryJob(id, kind, outcome, state.progress(), error);
      }
      // Under the lock, so a follower arriving now either sees the finish or is told it here.
      synchronized (this) {
        followers.forEach(follower -> follower.accept(next));
        followers.clear();
      }
    }

    /** Registers first, then reports the current state, so no change falls between the two. */
    Runnable follow(Consumer<LibraryJob> follower) {
      synchronized (this) {
        if (state.finished()) {
          follower.accept(state);
          return () -> {};
        }
        followers.add(follower);
      }
      follower.accept(snapshot());
      return () -> followers.remove(follower);
    }
  }
}
