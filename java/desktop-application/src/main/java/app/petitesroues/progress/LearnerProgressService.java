package app.petitesroues.progress;

import app.petitesroues.comic.ReadingProgressService;
import app.petitesroues.library.persistence.CatalogueStore;
import app.petitesroues.model.progress.ProgressPosition;
import app.petitesroues.model.progress.SavedProgress;
import app.petitesroues.playback.PlaybackProgressService;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Dispatches the one title-progress resource to its media-specific validation and persistence. */
@Service
public class LearnerProgressService {
  private final CatalogueStore titles;
  private final PlaybackProgressService playback;
  private final ReadingProgressService reading;

  public LearnerProgressService(
      CatalogueStore titles, PlaybackProgressService playback, ReadingProgressService reading) {
    this.titles = titles;
    this.playback = playback;
    this.reading = reading;
  }

  private boolean comic(String id) {
    var entry =
        titles.find(id).orElseThrow(() -> new NoSuchElementException("Unknown title: " + id));
    return entry.location() != null && "comic".equals(entry.location().documentType());
  }

  public SavedProgress load(String id) throws IOException {
    return comic(id) ? reading.load(id) : playback.load(id);
  }

  public void clear(String id) throws IOException {
    if (comic(id)) reading.clear(id);
    else playback.clear(id);
  }

  public SavedProgress save(String id, ProgressPosition position) throws IOException {
    boolean comic = comic(id);
    return switch (position) {
      case ProgressPosition.Comic p when comic -> reading.save(id, p.pageId());
      case ProgressPosition.Video p when !comic -> playback.save(id, p.positionSeconds());
      default ->
          throw new IllegalArgumentException("Position does not match the title's media type");
    };
  }
}
