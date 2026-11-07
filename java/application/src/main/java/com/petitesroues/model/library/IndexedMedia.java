package com.petitesroues.model.library;

import java.nio.file.Path;

/**
 * A video or CBZ found in a registered folder at its last refresh, with what probing it learned.
 * Media links to a study document when its fingerprint equals the document's reference fingerprint,
 * wherever either file lives.
 *
 * @param repositoryId the registered folder it was found in
 * @param repositoryPath that folder's absolute path
 * @param relativePath path below the folder, with forward slashes
 * @param kind {@code video} or {@code comic}
 * @param sizeBytes file size when probed
 * @param modifiedMillis modification time when probed; with the size, decides whether a refresh
 *     probes it again
 * @param fingerprint v1 media fingerprint
 * @param durationSeconds container duration for a video, or null when unknown or a comic
 * @param audioCodecs ffprobe codec names of a video's audio streams, comma-separated; empty when it
 *     has none; null for a comic
 * @param audioPlayable whether any audio stream is browser-playable; null for a comic
 * @param posterPath its own card image, relative to the folder, or null ({@link
 *     com.petitesroues.library.discovery.ContentScanner} finds posters at Refresh)
 * @param seriesPosterPath its series poster, relative to the folder, or null
 * @param posterProblem why a poster could not be chosen, or null
 */
public record IndexedMedia(
    String repositoryId,
    String repositoryPath,
    String relativePath,
    String kind,
    long sizeBytes,
    long modifiedMillis,
    String fingerprint,
    Double durationSeconds,
    String audioCodecs,
    Boolean audioPlayable,
    String posterPath,
    String seriesPosterPath,
    String posterProblem) {

  /** Media without posters, as tests and callers that do not track them build it. */
  public IndexedMedia(
      String repositoryId,
      String repositoryPath,
      String relativePath,
      String kind,
      long sizeBytes,
      long modifiedMillis,
      String fingerprint,
      Double durationSeconds,
      String audioCodecs,
      Boolean audioPlayable) {
    this(
        repositoryId,
        repositoryPath,
        relativePath,
        kind,
        sizeBytes,
        modifiedMillis,
        fingerprint,
        durationSeconds,
        audioCodecs,
        audioPlayable,
        null,
        null,
        null);
  }

  /** The file's absolute location; it may have gone since the last refresh. */
  public Path file() {
    return Path.of(repositoryPath).resolve(relativePath).normalize();
  }

  /** The file's own poster, or null; it may have gone since the last refresh. */
  public Path poster() {
    return posterPath == null ? null : Path.of(repositoryPath).resolve(posterPath).normalize();
  }

  /** The file's series poster, or null; it may have gone since the last refresh. */
  public Path seriesPoster() {
    return seriesPosterPath == null
        ? null
        : Path.of(repositoryPath).resolve(seriesPosterPath).normalize();
  }

  /** Whether this is a video whose audio no browser can play. */
  public boolean unplayableAudio() {
    return "video".equals(kind) && Boolean.FALSE.equals(audioPlayable);
  }

  /** The warning for {@link #unplayableAudio()}, naming the codecs found. */
  public String audioWarning() {
    return audioCodecs == null || audioCodecs.isBlank()
        ? "No audio track"
        : "No audio track this player can play (" + audioCodecs.toUpperCase() + ")";
  }
}
