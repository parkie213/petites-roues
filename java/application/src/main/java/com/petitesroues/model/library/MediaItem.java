package com.petitesroues.model.library;

import java.util.List;

/**
 * A video or CBZ in a registered folder that needs the user's attention, for browsing: no study
 * document references it, or no browser can play its audio. Local absolute paths are omitted.
 *
 * @param repositoryId the folder it was found in
 * @param relativePath path below that folder
 * @param kind {@code video} or {@code comic}
 * @param durationSeconds a video's duration, or null
 * @param warnings what needs fixing, in display order; never empty
 */
public record MediaItem(
    String repositoryId,
    String relativePath,
    String kind,
    Double durationSeconds,
    List<String> warnings) {
  public MediaItem {
    warnings = List.copyOf(warnings);
  }
}
