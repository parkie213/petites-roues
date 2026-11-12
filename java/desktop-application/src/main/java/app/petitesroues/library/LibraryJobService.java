package app.petitesroues.library;

import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.model.library.LibraryJob;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
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
public class LibraryJobService implements DisposableBean {
  private static final System.Logger LOG = System.getLogger(LibraryJobService.class.getName());

  /** The work a job does, reporting progress as it goes. */
  @FunctionalInterface
  public interface Work {
    void run(LibraryProgress.Listener progress) throws Exception;
  }

  private final LibraryUpdateService updates;
  private final ExecutorService worker;
  private final ExecutorService notifications;
  private final Duration shutdownWait;
  private boolean closed;
  private Job latest;

  @Autowired
  public LibraryJobService(LibraryUpdateService updates) {
    this(
        updates,
        Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("library-job").factory()),
        Executors.newVirtualThreadPerTaskExecutor(),
        Duration.ofSeconds(5));
  }

  /** Takes ownership of both executors, including their shutdown. */
  LibraryJobService(
      LibraryUpdateService updates,
      ExecutorService worker,
      ExecutorService notifications,
      Duration shutdownWait) {
    this.updates = updates;
    this.worker = worker;
    this.notifications = notifications;
    this.shutdownWait = shutdownWait;
  }

  /** Starts {@code work} in the background, or returns the job already running. */
  public synchronized LibraryJob start(String kind, Work work) {
    if (closed) throw new IllegalStateException("Library jobs are shutting down");
    if (latest != null && !latest.snapshot().finished()) return latest.snapshot();
    var job = new Job(UUID.randomUUID().toString(), kind);
    worker.execute(() -> run(job, work));
    latest = job;
    return job.snapshot();
  }

  /** The running job, or else the last one to finish; empty before any job has run. */
  public synchronized Optional<LibraryJob> latest() {
    return Optional.ofNullable(latest).map(Job::snapshot);
  }

  /**
   * Delivers the current state and subsequent updates asynchronously in order. Slow followers
   * retain at most 64 pending states; older intermediate progress may be skipped. The terminal
   * state is delivered once, unless the follower disconnects, throws, or shutdown interrupts it.
   * Returns a handle that stops delivery without affecting the job.
   *
   * @throws NoSuchElementException if the job is not the latest one
   */
  public synchronized Runnable follow(String id, Consumer<LibraryJob> follower) {
    if (closed) throw new IllegalStateException("Library jobs are shutting down");
    if (latest == null || !latest.id.equals(id))
      throw new NoSuchElementException("No such job: " + id);
    return latest.follow(follower);
  }

  private void run(Job job, Work work) {
    try {
      synchronized (updates) {
        if (Thread.currentThread().isInterrupted() || job.snapshot().finished()) return;
        work.run(job::progress);
      }
      job.finish("succeeded", null);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      job.finish("failed", "Server is shutting down");
    } catch (IllegalArgumentException | NoSuchElementException expected) {
      job.finish("failed", expected.getMessage());
    } catch (Exception unexpected) {
      LOG.log(System.Logger.Level.ERROR, "Library job failed", unexpected);
      job.finish("failed", "Something went wrong; the server log has the details");
    }
  }

  /** Stops admission, allows bounded completion, then interrupts outstanding work and delivery. */
  @Override
  public void destroy() {
    synchronized (this) {
      if (closed) return;
      closed = true;
      worker.shutdown();
    }
    await(worker);
    synchronized (this) {
      if (latest != null) latest.finish("failed", "Server is shutting down");
      notifications.shutdown();
    }
    await(notifications);
  }

  private void await(ExecutorService executor) {
    try {
      if (!executor.awaitTermination(shutdownWait.toMillis(), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(shutdownWait.toMillis(), TimeUnit.MILLISECONDS))
          LOG.log(
              System.Logger.Level.WARNING, "Library job executor did not stop after interruption");
      }
    } catch (InterruptedException interrupted) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * One job's state; enqueueing under its monitor orders initial, progress and terminal delivery.
   */
  private final class Job {
    final String id;
    final String kind;
    final List<Follower> followers = new ArrayList<>();
    private LibraryJob state;

    Job(String id, String kind) {
      this.id = id;
      this.kind = kind;
      this.state = new LibraryJob(id, kind, "running", null, null);
    }

    synchronized LibraryJob snapshot() {
      return state;
    }

    synchronized void progress(LibraryProgress progress) {
      if (state.finished()) return;
      state = new LibraryJob(id, kind, "running", progress, null);
      followers.forEach(follower -> follower.offer(state));
    }

    synchronized void finish(String outcome, String error) {
      if (state.finished()) return;
      state = new LibraryJob(id, kind, outcome, state.progress(), error);
      followers.forEach(follower -> follower.offer(state));
      followers.clear();
    }

    synchronized Runnable follow(Consumer<LibraryJob> consumer) {
      var follower = new Follower(consumer);
      follower.offer(state);
      if (!state.finished()) followers.add(follower);
      Future<?> delivery = notifications.submit(follower::deliver);
      return () -> {
        synchronized (this) {
          followers.remove(follower);
        }
        delivery.cancel(true);
      };
    }

    /** A bounded mailbox isolates a slow or broken observer from indexing and other observers. */
    private final class Follower {
      private final Consumer<LibraryJob> consumer;
      private final ArrayBlockingQueue<LibraryJob> pending = new ArrayBlockingQueue<>(64);

      Follower(Consumer<LibraryJob> consumer) {
        this.consumer = consumer;
      }

      void offer(LibraryJob next) {
        if (!pending.offer(next)) {
          pending.poll();
          pending.offer(next);
        }
      }

      void deliver() {
        try {
          while (true) {
            var next = pending.take();
            consumer.accept(next);
            if (next.finished()) return;
          }
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
          LOG.log(System.Logger.Level.DEBUG, "Library job follower disconnected", failure);
        } finally {
          synchronized (Job.this) {
            followers.remove(this);
          }
        }
      }
    }
  }
}
