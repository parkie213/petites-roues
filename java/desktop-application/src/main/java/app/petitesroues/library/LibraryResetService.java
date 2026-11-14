package app.petitesroues.library;

import app.petitesroues.library.persistence.LibraryResetStore;
import app.petitesroues.library.progress.LibraryProgress;
import app.petitesroues.study.ReviewQueue;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resets the library: deletes the catalogue index, progress, review history and phone pairings,
 * then rebuilds the index from the registered folders. Registered folders and settings are kept,
 * and no file on disk is touched.
 *
 * <p>The guard and the wipe share one transaction, so a reset either changes nothing or deletes
 * everything. The rebuild is a separate step that reads files, so it never runs inside that
 * transaction. If it fails, the folders stay registered and the library stays empty or partial; a
 * later Refresh finishes the rebuild. Overlap with other library jobs is prevented by {@link
 * LibraryJobService}, not here.
 */
@Service
public class LibraryResetService {
  private final LibraryResetStore store;
  private final ReviewQueue queue;
  private final ContentRepositoryService repositories;
  private final TransactionTemplate transactions;

  public LibraryResetService(
      LibraryResetStore store,
      ReviewQueue queue,
      ContentRepositoryService repositories,
      TransactionTemplate transactions) {
    this.store = store;
    this.queue = queue;
    this.repositories = repositories;
    this.transactions = transactions;
  }

  /**
   * Wipes the library, then refreshes every registered folder, reporting each folder's progress.
   *
   * @throws DocumentConflictException if edits are pending or a review batch is open; nothing was
   *     deleted
   * @throws IllegalArgumentException naming the folder whose refresh failed, after the wipe
   */
  public void reset(LibraryProgress.Listener progress) throws IOException {
    transactions.executeWithoutResult(
        status -> {
          int pending = queue.unreviewedCount();
          if (pending > 0)
            throw new DocumentConflictException(
                pending
                    + (pending == 1 ? " edit is" : " edits are")
                    + " pending: process or discard them first");
          if (queue.openBatchCount() > 0)
            throw new DocumentConflictException(
                "A review batch is open: complete or discard it first");
          store.wipe();
        });
    repositories.refreshAll(progress);
  }
}
