package app.petitesroues.model.speech;

import java.time.Instant;
import java.util.List;

/**
 * Catalogue metadata with current installation flags. The fetch time advances only on successful
 * provider retrieval; stale means over an hour old. A warning explains refresh or persistence
 * failures while this snapshot remains usable.
 */
public record VoiceCatalogue(
    List<CatalogueVoice> voices, Instant fetchedAt, boolean stale, String warning) {
  public VoiceCatalogue {
    voices = List.copyOf(voices);
  }
}
