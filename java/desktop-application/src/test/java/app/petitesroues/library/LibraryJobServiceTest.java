package app.petitesroues.library;

import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.model.library.LibraryJob;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LibraryJobServiceTest {
  private static void shutdown(
      LibraryJobService service, ExecutorService worker, ExecutorService notifications)
      throws InterruptedException {
    service.stop(Duration.ofMillis(100));
    worker.shutdownNow();
    notifications.shutdown();
    if (!notifications.awaitTermination(1, TimeUnit.SECONDS)) notifications.shutdownNow();
    assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
    assertTrue(notifications.awaitTermination(5, TimeUnit.SECONDS));
  }

  @Test
  void slowAndThrowingFollowersCannotFailOrBlockWork() throws Exception {
    var worker = Executors.newSingleThreadExecutor();
    var notifications = Executors.newVirtualThreadPerTaskExecutor();
    var service = new LibraryJobService(new LibraryUpdateService(null), worker, notifications);
    var releaseWork = new CountDownLatch(1);
    var slowEntered = new CountDownLatch(1);
    var releaseSlow = new CountDownLatch(1);
    var workDone = new CountDownLatch(1);
    var received = new LinkedBlockingQueue<LibraryJob>();
    var slowReceived = new LinkedBlockingQueue<LibraryJob>();
    try {
      var job =
          service.start(
              "refresh",
              progress -> {
                releaseWork.await();
                for (int i = 0; i < 100; i++)
                  progress.report(new LibraryProgress("repo", "media", i, 100, null));
                workDone.countDown();
              });
      service.follow(
          job.id(),
          state -> {
            slowReceived.add(state);
            slowEntered.countDown();
            try {
              releaseSlow.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });
      service.follow(
          job.id(),
          state -> {
            throw new IllegalStateException("disconnected");
          });
      service.follow(job.id(), received::add);
      assertTrue(slowEntered.await(5, TimeUnit.SECONDS));
      releaseWork.countDown();
      assertTrue(workDone.await(5, TimeUnit.SECONDS));
      LibraryJob last;
      int previous = -1;
      do {
        last = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(last);
        if (last.progress() != null) {
          assertTrue(last.progress().done() >= previous);
          previous = last.progress().done();
        }
      } while (!last.finished());
      assertEquals("succeeded", last.state());
      assertNull(received.poll());
      releaseSlow.countDown();
      int slowStates = 0;
      LibraryJob slow;
      do {
        slow = slowReceived.poll(5, TimeUnit.SECONDS);
        assertNotNull(slow);
        slowStates++;
      } while (!slow.finished());
      assertEquals(last, slow);
      assertTrue(slowStates <= 65, "one active callback and at most 64 pending states");
      var terminal = new LinkedBlockingQueue<LibraryJob>();
      service.follow(job.id(), terminal::add);
      assertEquals(last, terminal.poll(5, TimeUnit.SECONDS));
    } finally {
      releaseWork.countDown();
      releaseSlow.countDown();
      shutdown(service, worker, notifications);
    }
    assertTrue(worker.isTerminated());
    assertTrue(notifications.isTerminated());
  }

  @Test
  void disconnectStopsDeliveryAndShutdownAllowsSuccessfulWorkToFinish() throws Exception {
    var worker = Executors.newSingleThreadExecutor();
    var notifications = Executors.newVirtualThreadPerTaskExecutor();
    var service = new LibraryJobService(new LibraryUpdateService(null), worker, notifications);
    var release = new CountDownLatch(1);
    var observed = new CountDownLatch(1);
    var calls = new AtomicInteger();
    try {
      var job = service.start("refresh", progress -> release.await());
      var stop =
          service.follow(
              job.id(),
              state -> {
                calls.incrementAndGet();
                observed.countDown();
              });
      assertTrue(observed.await(5, TimeUnit.SECONDS));
      stop.run();
      release.countDown();
      service.stop(Duration.ofSeconds(1));
      assertFalse(worker.isShutdown(), "the service borrows the executor");
      assertFalse(notifications.isShutdown(), "the service borrows the executor");
      shutdown(service, worker, notifications);
      assertEquals("succeeded", service.latest().orElseThrow().state());
      assertEquals(1, calls.get());
      assertTrue(worker.isTerminated());
      assertTrue(notifications.isTerminated());
    } finally {
      release.countDown();
      shutdown(service, worker, notifications);
    }
  }

  @Test
  void concurrentSubmissionsShareOneJobAndShutdownInterruptsIt() throws Exception {
    var worker = Executors.newVirtualThreadPerTaskExecutor();
    var notifications = Executors.newVirtualThreadPerTaskExecutor();
    var service = new LibraryJobService(new LibraryUpdateService(null), worker, notifications);
    var entered = new CountDownLatch(1);
    var interrupted = new CountDownLatch(1);
    var calls = new AtomicInteger();
    try {
      var first =
          service.start(
              "refresh",
              progress -> {
                calls.incrementAndGet();
                entered.countDown();
                try {
                  new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                  interrupted.countDown();
                  throw e;
                }
              });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      try (var submitters = Executors.newVirtualThreadPerTaskExecutor()) {
        var futures =
            IntStream.range(0, 20)
                .mapToObj(
                    i ->
                        submitters.submit(
                            () -> service.start("add", progress -> calls.incrementAndGet())))
                .toList();
        for (var future : futures) assertEquals(first.id(), future.get(5, TimeUnit.SECONDS).id());
      }
      shutdown(service, worker, notifications);
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
      assertTrue(service.latest().orElseThrow().finished());
      assertEquals(1, calls.get());
      assertThrows(IllegalStateException.class, () -> service.start("refresh", progress -> {}));
      shutdown(service, worker, notifications);
    } finally {
      shutdown(service, worker, notifications);
    }
  }
}
