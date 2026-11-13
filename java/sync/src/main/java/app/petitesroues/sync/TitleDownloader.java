package app.petitesroues.sync;

import app.petitesroues.sync.model.Desktop;
import app.petitesroues.sync.model.DesktopTitle;
import java.io.IOException;
import java.util.concurrent.CancellationException;

/**
 * Carries out a {@link TransferPlan}: downloads each file through the {@link DesktopClient} into
 * the {@link TransferFolder}, reporting progress, and stops between chunks when asked to. Stopping
 * or a failure keeps the current part, so running a fresh plan later resumes it.
 */
public final class TitleDownloader {
  /** What the downloader reports, and how it is asked to stop. */
  public interface Listener {
    /**
     * {@code done} of the plan's {@code total} bytes are downloaded, now fetching for {@code
     * title}.
     */
    default void progress(DesktopTitle title, long done, long total) {}

    /** Every file of {@code title} is in place. */
    default void finished(DesktopTitle title) {}

    /** Whether to stop as soon as possible. */
    default boolean cancelled() {
      return false;
    }
  }

  private final DesktopClient client;

  public TitleDownloader(DesktopClient client) {
    this.client = client;
  }

  /**
   * Downloads the plan's files in order.
   *
   * @throws CancellationException if the listener asked to stop
   * @throws IOException if a file could not be downloaded or did not match the desktop's
   */
  public void run(Desktop desktop, TransferPlan plan, TransferFolder folder, Listener listener)
      throws IOException {
    long[] done = {0};
    for (var step : plan.steps()) {
      if (listener.cancelled()) throw new CancellationException();
      listener.progress(step.title(), done[0], plan.remaining());
      client.download(
          desktop,
          step.file(),
          folder,
          bytes -> {
            done[0] += bytes;
            listener.progress(step.title(), Math.min(done[0], plan.remaining()), plan.remaining());
          },
          listener::cancelled);
      if (step.last()) listener.finished(step.title());
    }
  }
}
