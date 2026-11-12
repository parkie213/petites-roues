package app.petitesroues.library;

import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.model.library.LibraryJob;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one Add or Refresh at a time and retains its latest progress for polling and page reloads.
 * Work holds the shared edit/refresh monitor; the JDK task owns completion, failure and
 * cancellation. Only the latest job is retained, in memory. The executor is borrowed from the
 * composition layer.
 */
public class LibraryJobService implements AutoCloseable {
  private static final System.Logger LOG = System.getLogger(LibraryJobService.class.getName());

  /** Repository work reports domain progress; it does not manage scheduling or transport. */
  @FunctionalInterface
  public interface Work {
    void run(LibraryProgress.Listener progress) throws Exception;
  }

  private final LibraryUpdateService updates;
  private final Executor executor;
  private Job latest;
  private boolean closed;

  public LibraryJobService(LibraryUpdateService updates, Executor executor) {
    this.updates = updates;
    this.executor = executor;
  }

  /** Starts work, or returns the current job when one is already running. */
  public synchronized LibraryJob start(String kind, Work work) {
    if (closed) throw new IllegalStateException("Library jobs are shutting down");
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

  /** Stops admission, allows five seconds for work, then requests cooperative interruption. */
  @Override
  public void close() {
    close(Duration.ofSeconds(5));
  }

  /** Bounds the wait for this service's work, without closing the borrowed executor. */
  void close(Duration grace) {
    Future<?> task;
    synchronized (this) {
      if (closed) return;
      closed = true;
      task = latest == null ? null : latest.task;
    }
    if (task == null) return;
    try {
      task.get(grace.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timeout) {
      task.cancel(true);
    } catch (InterruptedException interrupted) {
      task.cancel(true);
      Thread.currentThread().interrupt();
    } catch (ExecutionException failed) {
      // Reported by the task and exposed through its snapshot.
    }
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
                    LOG.log(System.Logger.Level.ERROR, "Library job failed", failure);
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
