package com.petitesroues.media;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Learns what the library needs to know about one media file: its v1 fingerprint, and for a video
 * its duration and its audio tracks' codecs and languages. Reads at most 8 MiB of the file plus
 * what ffprobe reads. A video ffprobe cannot read (or a missing ffprobe) is recorded with unknown
 * duration and audio rather than failing the refresh, so it raises no audio warning.
 */
@Component
public class MediaProbe {
  private final Ffmpeg ffmpeg;

  public MediaProbe(Ffmpeg ffmpeg) {
    this.ffmpeg = ffmpeg;
  }

  /**
   * @param durationSeconds container duration, or null when unknown or not a video
   * @param audioCodecs comma-separated audio codec names, empty for none; null when unknown or not
   *     a video
   * @param audioPlayable whether the first audio track, the one browsers play, is browser-playable;
   *     null when unknown or not a video
   * @param audioLanguages each audio track's language in track order ({@link AudioLanguages}),
   *     comma-separated, an untagged track empty; null when unknown or not a video
   */
  public record Probe(
      String fingerprint,
      Double durationSeconds,
      String audioCodecs,
      Boolean audioPlayable,
      String audioLanguages) {}

  public Probe video(Path file) throws IOException {
    String fingerprint = MediaFingerprint.of(file);
    try {
      var tracks = ffmpeg.audioTracks(file);
      Double duration;
      try {
        duration = ffmpeg.duration(file);
      } catch (IOException noDuration) {
        duration = null;
      }
      return new Probe(
          fingerprint,
          duration,
          tracks.stream().map(Ffmpeg.AudioTrack::codec).distinct().collect(Collectors.joining(",")),
          !tracks.isEmpty() && tracks.getFirst().playable(),
          tracks.stream()
              .map(track -> AudioLanguages.tag(track.language()))
              .collect(Collectors.joining(",")));
    } catch (IOException | RuntimeException unreadable) {
      return new Probe(fingerprint, null, null, null, null);
    }
  }

  public Probe comic(Path file) throws IOException {
    return new Probe(MediaFingerprint.of(file), null, null, null, null);
  }
}
