package app.petitesroues.playback;

import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.progress.PlaybackPosition;
import app.petitesroues.model.progress.PlaybackProgress;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates local resume positions and coordinates catalogue existence checks with progress
 * storage. Refreshing a repository preserves progress; unregistering a title deletes it through its
 * foreign key.
 */
@Service
public class PlaybackProgressService {
  private final CatalogueStore titles;
  private final PlaybackProgressStore progress;

  public PlaybackProgressService(CatalogueStore titles, PlaybackProgressStore progress) {
    this.titles = titles;
    this.progress = progress;
  }

  @Transactional(readOnly = true)
  public PlaybackProgress load(String titleId) {
    requireVideo(titleId);
    return progress.find(titleId);
  }

  private void requireVideo(String titleId) {
    var title =
        titles
            .find(titleId)
            .orElseThrow(() -> new NoSuchElementException("Unknown title: " + titleId));
    if (title.location() != null && "comic".equals(title.location().documentType()))
      throw new IllegalArgumentException("Playback progress requires a video title");
  }

  /** Forgets a video's resume position, so it opens at the start and shows no progress. */
  @Transactional
  public void clear(String titleId) {
    requireVideo(titleId);
    progress.delete(titleId);
  }

  /**
   * Accepts finite nonnegative seconds; the browser clamps restored positions to media duration.
   */
  @Transactional
  public PlaybackProgress save(String titleId, Double positionSeconds) {
    if (positionSeconds == null)
      throw new IllegalArgumentException("Playback position must be finite and nonnegative");
    var position = new PlaybackPosition(positionSeconds);
    requireVideo(titleId);
    var saved = new PlaybackProgress(position.seconds(), Instant.now().toString());
    progress.save(titleId, saved);
    return saved;
  }
}
