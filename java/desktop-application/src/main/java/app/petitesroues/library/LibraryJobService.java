package app.petitesroues.library;

import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.model.library.LibraryJob;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one Add or Refresh at a time and retains its latest progress for polling and page reloads.
 * Work holds the shared edit/refresh monitor; the JDK task owns completion, failure and
 * cancellation. Only the latest job is retained, in memory. The executor is borrowed from the
 * composition layer, which owns admission during shutdown and termination of running work.
 */
public class LibraryJobService {
  private static final Logger LOG = LoggerFactory.getLogger(LibraryJobService.class);

  /** Repository work reports domain progress; it does not manage scheduling or transport. */
  @FunctionalInterface
  public interface Work {
    void run(LibraryProgress.Listener progress) throws Exception;
  }

  private final LibraryUpdateService updates;
  private final Executor executor;
  private Job latest;

  public LibraryJobService(LibraryUpdateService updates, Executor executor) {
    this.updates = updates;
    this.executor = executor;
  }

  /** Starts work, or returns the current job when one is already running. */
  public synchronized LibraryJob start(String kind, Work work) {
    if (latest != null && !latest.task.isDone()) return latest.snapshot();
    var job = new Job(kind, work);
    executor.execute(job.task);
    latest = job;
    return job.snapshot();
  }

  /** The running job or last finished job; empty before any work is submitted. */
  public synchronized Optional<LibraryJob> latest() {
    return Optional.ofNullable(latest).map(Job::snapshot);
  }

  /** Returns the retained job; earlier jobs are not kept as a history. */
  public synchronized LibraryJob get(String id) {
    if (latest == null || !latest.id.equals(id))
      throw new NoSuchElementException("No such job: " + id);
    return latest.snapshot();
  }

  /** Application identity and progress surround the JDK's execution state. */
  private final class Job {
    final String id = UUID.randomUUID().toString();
    final String kind;
    final FutureTask<Void> task;
    volatile LibraryProgress progress;

    Job(String kind, Work work) {
      this.kind = kind;
      task =
          new FutureTask<>(
              () -> {
                try {
                  synchronized (updates) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    work.run(next -> progress = next);
                  }
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                  throw interrupted;
                } catch (Exception failure) {
                  if (!(failure instanceof IllegalArgumentException
                      || failure instanceof NoSuchElementException))
                    LOG.error("Library job failed", failure);
                  throw failure;
                }
                return null;
              });
    }

    LibraryJob snapshot() {
      return switch (task.state()) {
        case RUNNING -> new LibraryJob(id, kind, "running", progress, null);
        case SUCCESS -> new LibraryJob(id, kind, "succeeded", progress, null);
        case CANCELLED -> new LibraryJob(id, kind, "failed", progress, "Server is shutting down");
        case FAILED -> {
          var failure = task.exceptionNow();
          String message =
              failure instanceof InterruptedException
                  ? "Server is shutting down"
                  : failure instanceof IllegalArgumentException
                          || failure instanceof NoSuchElementException
                      ? failure.getMessage()
                      : "Something went wrong; the server log has the details";
          yield new LibraryJob(id, kind, "failed", progress, message);
        }
      };
    }
  }
}
