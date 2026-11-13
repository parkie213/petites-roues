package app.petitesroues.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lists a video's audio tracks and duration with ffprobe and writes thumbnails with ffmpeg. Both
 * tools must be on the PATH. Browsers play only a file's first audio track, whichever the file
 * marks as default, and cannot decode AC3, E-AC3 or DTS, so each track records whether a browser
 * could play it.
 */
@Component
public class Ffmpeg {
  /** Audio codecs Chrome, Firefox and Safari all decode in MP4 or WebM. */
  private static final Set<String> PLAYABLE = Set.of("aac", "mp3", "opus", "flac", "vorbis");

  private final ObjectMapper mapper;
  private final ProcessRunner processes;

  public Ffmpeg(ObjectMapper mapper, ProcessRunner processes) {
    this.mapper = mapper;
    this.processes = processes;
  }

  /**
   * One audio stream of a video file.
   *
   * @param index ffprobe stream index, used to select the track
   * @param codec ffprobe codec name, such as aac or ac3
   * @param language ISO 639 language tag from the file, or null
   * @param title track title from the file (often naming the dub, such as VFF), or null
   * @param defaultTrack whether the file marks this as its default audio track
   * @param playable whether browsers can decode this codec
   */
  public record AudioTrack(
      int index,
      String codec,
      String language,
      String title,
      boolean defaultTrack,
      boolean playable) {}

  public List<AudioTrack> audioTracks(Path video) throws IOException {
    var tracks = new ArrayList<AudioTrack>();
    for (JsonNode stream : streams(video)) {
      if (!"audio".equals(stream.path("codec_type").asString(""))) continue;
      String codec = stream.path("codec_name").asString("");
      JsonNode tags = stream.path("tags");
      tracks.add(
          new AudioTrack(
              stream.path("index").asInt(),
              codec,
              text(tags, "language"),
              text(tags, "title"),
              stream.path("disposition").path("default").asInt() == 1,
              PLAYABLE.contains(codec)));
    }
    return tracks;
  }

  /** The container's duration in seconds as ffprobe reports it, to the millisecond. */
  public double duration(Path video) throws IOException {
    var output =
        processes.run(
            List.of("ffprobe", "-v", "error", "-show_format", "-of", "json", video.toString()));
    double duration = mapper.readTree(output).path("format").path("duration").asDouble(0);
    if (!(duration > 0)) throw new IOException("ffprobe reports no duration for " + video);
    return Math.round(duration * 1000) / 1000.0;
  }

  private JsonNode streams(Path video) throws IOException {
    var output =
        processes.run(
            List.of("ffprobe", "-v", "error", "-show_streams", "-of", "json", video.toString()));
    return mapper.readTree(output).path("streams");
  }

  /**
   * Writes {@code target} as a JPEG of {@code image} (JPEG, PNG or WebP) at most {@code width}
   * pixels wide, keeping its aspect ratio; smaller images are not enlarged. The file appears
   * complete or not at all, even when several threads write the same target at once: each writes
   * its own temporary file beside it and moves that into place.
   */
  public void thumbnail(Path image, int width, Path target) throws IOException {
    Path partial =
        Files.createTempFile(target.getParent(), target.getFileName() + ".", ".partial.jpg");
    try {
      processes.run(
          List.of(
              "ffmpeg",
              "-nostdin",
              "-v",
              "error",
              "-y",
              "-i",
              image.toString(),
              "-frames:v",
              "1",
              "-vf",
              "scale='min(" + width + ",iw)':-2",
              "-q:v",
              "3",
              "-update",
              "1",
              partial.toString()));
      Files.move(
          partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(partial);
    }
  }

  private static String text(JsonNode node, String field) {
    String value = node.path(field).asString("");
    return value.isBlank() ? null : value;
  }
}
