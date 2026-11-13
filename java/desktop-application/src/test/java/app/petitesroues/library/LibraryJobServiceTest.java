package app.petitesroues.library;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.study.StateConflictException;
import java.time.Duration;
import java.util.List;
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
        var jobs = new LibraryJobService(new LibraryUpdateService(null), executor);
        var entered = new CountDownLatch(1);
        var progress = new LibraryProgress("repo", "media", 2, 3, "video.mp4");
        var first =
            jobs.start(
                "refresh",
                "same-command",
                listener -> {
                  listener.report(progress);
                  entered.countDown();
                  release.await();
                  return List.of();
                });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals(progress, jobs.get(first.id()).progress());
        var attempts =
            IntStream.range(0, 20)
                .mapToObj(
                    i ->
                        executor.submit(
                            () ->
                                jobs.start(
                                    "refresh", "same-command", listener -> fail("duplicate work"))))
                .toList();
        for (var attempt : attempts)
          assertEquals(first.id(), attempt.get(5, TimeUnit.SECONDS).id());
        assertThrows(
            StateConflictException.class,
            () -> jobs.start("refresh", "different-target", listener -> List.of()));
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
  void failedSubmissionDoesNotPublishAPhantomJob() {
    var jobs =
        new LibraryJobService(
            new LibraryUpdateService(null),
            task -> {
              throw new RejectedExecutionException();
            });
    assertThrows(
        RejectedExecutionException.class,
        () -> jobs.start("add", "add-command", listener -> List.of()));
    assertTrue(jobs.latest().isEmpty());
  }
}
