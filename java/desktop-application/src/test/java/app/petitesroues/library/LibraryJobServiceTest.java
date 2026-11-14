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
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      try {
        var jobs = new LibraryJobService(executor);
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
                    i ->
                        executor.submit(
                            () -> jobs.start("add", listener -> fail("duplicate work"))))
                .toList();
        for (var attempt : attempts)
          assertEquals(first.id(), attempt.get(5, TimeUnit.SECONDS).id());
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> jobs.get(first.id()).finished());
        assertEquals("succeeded", jobs.get(first.id()).state());
        assertEquals(progress, jobs.latest().orElseThrow().progress());
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void aResetCannotStartWhileAnotherJobRunsAndAConflictFailsWithItsMessage() throws Exception {
    var release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      try {
        var jobs = new LibraryJobService(executor);
        var running = jobs.start("refresh", listener -> release.await());
        var attempt = jobs.start("reset", listener -> fail("reset overlapped a running job"));
        assertEquals(running.id(), attempt.id());
        assertEquals("refresh", attempt.kind());
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> jobs.get(running.id()).finished());

        var refused =
            jobs.start(
                "reset",
                listener -> {
                  throw new DocumentConflictException("1 edit is pending: process or discard them");
                });
        await().atMost(Duration.ofSeconds(5)).until(() -> jobs.get(refused.id()).finished());
        assertEquals("failed", jobs.get(refused.id()).state());
        assertEquals("1 edit is pending: process or discard them", jobs.get(refused.id()).error());
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void failedSubmissionDoesNotPublishAPhantomJob() {
    var jobs =
        new LibraryJobService(
            task -> {
              throw new RejectedExecutionException();
            });
    assertThrows(RejectedExecutionException.class, () -> jobs.start("add", listener -> {}));
    assertTrue(jobs.latest().isEmpty());
  }
}
