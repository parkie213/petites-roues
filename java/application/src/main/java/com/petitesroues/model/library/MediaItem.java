package com.petitesroues.model.library;

import java.util.List;

/**
 * A video or CBZ found in a registered folder, for browsing, with the title it belongs to. Local
 * absolute paths are omitted.
 *
 * @param repositoryId the folder it was found in
 * @param relativePath path below that folder
 * @param kind {@code video} or {@code comic}
 * @param fingerprint its v1 media fingerprint, which names its card images
 * @param titleId the title whose study document references it, or null when none does
 * @param durationSeconds a video's duration, or null
 * @param posterAvailable whether it has its own poster
 * @param seriesPosterAvailable whether a series poster covers it
 * @param warnings what needs fixing, in display order: "No study document" when {@code titleId} is
 *     null, and unplayable audio; empty when nothing does
 */
public record MediaItem(
    String repositoryId,
    String relativePath,
    String kind,
    String fingerprint,
    String titleId,
    Double durationSeconds,
    boolean posterAvailable,
    boolean seriesPosterAvailable,
    List<String> warnings) {
  public MediaItem {
    warnings = List.copyOf(warnings);
  }
}
