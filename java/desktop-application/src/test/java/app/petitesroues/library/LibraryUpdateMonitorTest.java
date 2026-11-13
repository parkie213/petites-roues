package app.petitesroues.library;

import static app.petitesroues.support.DocumentFixtures.document;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.petitesroues.documents.StudyDocumentReader;
import app.petitesroues.library.discovery.ContentRepositoryScanner;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.library.persistence.ContentRepositoryStore;
import app.petitesroues.library.persistence.MediaStore;
import app.petitesroues.media.MediaProbe;
import app.petitesroues.model.library.CatalogueEntry;
import app.petitesroues.model.library.ContentRepository;
import app.petitesroues.study.DocumentEditingService;
import app.petitesroues.study.DocumentStore;
import app.petitesroues.study.ReviewQueue;
import app.petitesroues.study.ReviewQueueService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The focused concurrent-operation check for the shared {@link LibraryUpdateMonitor}: while a
 * player edit is running, repository removal and review-queue mutation wait for it, with no caller
 * coordinating anything. The mutating services acquire the monitor themselves, so this holds
 * however they are called.
 */
@ExtendWith(MockitoExtension.class)
class LibraryUpdateMonitorTest {
  private static final String REPOSITORY = "repository";
  private static final String TITLE = "title";

  @Mock ContentRepositoryStore repositories;
  @Mock CatalogueStore titles;
  @Mock ContentRepositoryScanner scanner;
  @Mock StudyDocumentReader reader;
  @Mock RepositoryContentService registration;
  @Mock ReviewQueue queue;
  @Mock MediaStore media;
  @Mock MediaProbe probe;
  @Mock TransactionTemplate transactions;

  private final CountDownLatch begun = new CountDownLatch(1);
  private final CountDownLatch release = new CountDownLatch(1);

  /** An edit service whose one comment holds the monitor until {@link #release} counts down. */
  private LibraryUpdateService heldEdit(LibraryUpdateMonitor monitor) throws IOException {
    var editing = mock(DocumentEditingService.class);
    when(editing.comment(TITLE, "cue", "held"))
        .thenAnswer(
            invocation -> {
              begun.countDown();
              release.await();
              return document(TITLE);
            });
    return new LibraryUpdateService(monitor, editing);
  }

  /** Executes the template's callback immediately, as a real transaction manager would. */
  private void runTransaction() {
    doAnswer(
            invocation -> {
              Consumer<TransactionStatus> work = invocation.getArgument(0);
              work.accept(null);
              return null;
            })
        .when(transactions)
        .executeWithoutResult(any());
  }

  /**
   * Releases the held edit before waiting for the executor's tasks, so a failed assertion cannot
   * leave a monitor-holding task stuck inside an executor that will not close.
   */
  private void close(ExecutorService executor) {
    release.countDown();
    executor.close();
  }

  @Test
  @Timeout(30)
  void repositoryRemovalWaitsForAPlayerEditThatHasBegun() throws Exception {
    var monitor = new LibraryUpdateMonitor();
    var updates = heldEdit(monitor);
    var repository =
        new ContentRepository(REPOSITORY, "Repository", Path.of("content").toString(), "old", 1, 0);
    var removalStarted = new CountDownLatch(1);
    when(repositories.find(REPOSITORY))
        .thenAnswer(
            invocation -> {
              removalStarted.countDown();
              return repository;
            });
    runTransaction();
    when(queue.unwrittenEdits(REPOSITORY)).thenReturn(0);
    var service =
        new ContentRepositoryService(
            monitor,
            repositories,
            scanner,
            reader,
            transactions,
            registration,
            queue,
            media,
            probe);

    var executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      var edit = executor.submit(() -> updates.comment(TITLE, "cue", "held"));
      assertTrue(begun.await(5, TimeUnit.SECONDS), "the edit must be running first");

      var removal = executor.submit(() -> service.remove(REPOSITORY));
      assertFalse(
          removalStarted.await(200, TimeUnit.MILLISECONDS),
          "removal must not begin while the edit holds the monitor");

      release.countDown();
      edit.get(5, TimeUnit.SECONDS);
      removal.get(5, TimeUnit.SECONDS);
      verify(repositories).delete(REPOSITORY);
    } finally {
      close(executor);
    }
  }

  @Test
  @Timeout(30)
  void reviewQueueMutationWaitsForAPlayerEditThatHasBegun() throws Exception {
    var monitor = new LibraryUpdateMonitor();
    var updates = heldEdit(monitor);
    var entry = new CatalogueEntry(TITLE, "Title", null, null, null, null, "document.json");
    var mutationStarted = new CountDownLatch(1);
    when(titles.find(TITLE))
        .thenAnswer(
            invocation -> {
              mutationStarted.countDown();
              return Optional.of(entry);
            });
    runTransaction();
    when(queue.read(entry)).thenReturn(List.of());
    var service =
        new ReviewQueueService(monitor, titles, mock(DocumentStore.class), queue, transactions);

    var executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      var edit = executor.submit(() -> updates.comment(TITLE, "cue", "held"));
      assertTrue(begun.await(5, TimeUnit.SECONDS), "the edit must be running first");

      var discard = executor.submit(() -> service.discard(TITLE, List.of()));
      assertFalse(
          mutationStarted.await(200, TimeUnit.MILLISECONDS),
          "the review-queue mutation must not begin while the edit holds the monitor");

      release.countDown();
      edit.get(5, TimeUnit.SECONDS);
      discard.get(5, TimeUnit.SECONDS);
      verify(queue).discard(entry);
    } finally {
      close(executor);
    }
  }
}
