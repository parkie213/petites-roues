package app.petitesroues.library;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.progress.LibraryProgress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LibraryJobServiceTest {
  @Test
  void concurrentSubmissionsShareOneJobAndProgressSurvivesCompletion() throws Exception {
    var release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
        var jobs = new LibraryJobService(new LibraryUpdateService(null), executor)) {
      var entered = new CountDownLatch(1);
      var progress = new LibraryProgress("repo", "media", 2, 3, "video.mp4");
      var first =
          jobs.start(
              "refresh",
              listener -> {
                listener.report(progress);
                entered.countDown();
                release.await();
              });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertEquals(progress, jobs.get(first.id()).progress());
      var attempts =
          IntStream.range(0, 20)
              .mapToObj(
                  i -> executor.submit(() -> jobs.start("add", listener -> fail("duplicate work"))))
              .toList();
      for (var attempt : attempts) assertEquals(first.id(), attempt.get(5, TimeUnit.SECONDS).id());
      release.countDown();
      await().atMost(Duration.ofSeconds(5)).until(() -> jobs.get(first.id()).finished());
      assertEquals("succeeded", jobs.get(first.id()).state());
      assertEquals(progress, jobs.latest().orElseThrow().progress());
    } finally {
      release.countDown();
    }
  }

  @Test
  void shutdownCancelsWorkAndLeavesTheBorrowedExecutorOpen() throws Exception {
    var interrupted = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var jobs = new LibraryJobService(new LibraryUpdateService(null), executor);
      var entered = new CountDownLatch(1);
      jobs.start(
          "refresh",
          listener -> {
            entered.countDown();
            try {
              new CountDownLatch(1).await();
            } catch (InterruptedException failure) {
              interrupted.countDown();
              throw failure;
            }
          });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      jobs.close(Duration.ZERO);
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      assertFalse(executor.isShutdown());
      assertEquals("failed", jobs.latest().orElseThrow().state());
      assertEquals("Server is shutting down", jobs.latest().orElseThrow().error());
      assertThrows(IllegalStateException.class, () -> jobs.start("add", listener -> {}));
      jobs.close();
    }
  }

  @Test
  void failedSubmissionDoesNotPublishAPhantomJob() {
    var jobs =
        new LibraryJobService(
            new LibraryUpdateService(null),
            task -> {
              throw new RejectedExecutionException();
            });
    assertThrows(RejectedExecutionException.class, () -> jobs.start("add", listener -> {}));
    assertTrue(jobs.latest().isEmpty());
    jobs.close();
  }
}
