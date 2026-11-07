package com.petitesroues.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lists a video's audio tracks with ffprobe and writes browser-playable copies with ffmpeg. Both
 * tools must be on the PATH. Browsers play only a file's default audio track and cannot decode AC3,
 * E-AC3 or DTS, so a playable copy keeps exactly one chosen track in a codec they support.
 */
@Component
public class Ffmpeg {
  /** Audio codecs Chrome, Firefox and Safari all decode in MP4 or WebM. */
  private static final Set<String> PLAYABLE = Set.of("aac", "mp3", "opus", "flac", "vorbis");

  /** Containers the browser plays directly when they hold one playable track. */
  private static final Set<String> PLAYABLE_CONTAINERS = Set.of("mp4", "m4v", "webm");

  private final ObjectMapper mapper;

  public Ffmpeg(ObjectMapper mapper) {
    this.mapper = mapper;
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
        run(List.of("ffprobe", "-v", "error", "-show_format", "-of", "json", video.toString()));
    double duration = mapper.readTree(output).path("format").path("duration").asDouble(0);
    if (!(duration > 0)) throw new IOException("ffprobe reports no duration for " + video);
    return Math.round(duration * 1000) / 1000.0;
  }

  private JsonNode streams(Path video) throws IOException {
    var output =
        run(List.of("ffprobe", "-v", "error", "-show_streams", "-of", "json", video.toString()));
    return mapper.readTree(output).path("streams");
  }

  /**
   * How much later the audio stream starts than the first video stream, in seconds. Desktop players
   * honour such a delay, but browsers ignore it in MP4 and play the audio early.
   */
  private double audioDelay(Path video, int audioIndex) throws IOException {
    double videoStart = 0, audioStart = 0;
    boolean seenVideo = false;
    for (JsonNode stream : streams(video)) {
      double start = stream.path("start_time").asDouble(0);
      if (!seenVideo && "video".equals(stream.path("codec_type").asString(""))) {
        videoStart = start;
        seenVideo = true;
      }
      if (stream.path("index").asInt(-1) == audioIndex) audioStart = start;
    }
    return audioStart - videoStart;
  }

  /**
   * Writes {@code target} as a browser-playable copy of {@code source} with only the chosen audio
   * track. A file that already has just that track, in a playable codec and container, starting
   * with the video, is copied byte for byte. Otherwise the picture is copied unchanged into MP4 and
   * the audio is copied or converted to AAC. Audio that starts later than the video is re-encoded
   * with that gap filled by silence, so it stays in sync in browsers. Returns the written file,
   * whose extension follows the result's container.
   *
   * @param target the output path without extension
   */
  public Path playableCopy(Path source, int audioIndex, Path target) throws IOException {
    var tracks = audioTracks(source);
    var chosen =
        tracks.stream()
            .filter(track -> track.index() == audioIndex)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No audio track " + audioIndex));
    String extension = extension(source);
    boolean delayed = Math.abs(audioDelay(source, audioIndex)) > 0.001;
    if (tracks.size() == 1
        && chosen.playable()
        && !delayed
        && PLAYABLE_CONTAINERS.contains(extension)) {
      Path copy = target.resolveSibling(target.getFileName() + "." + extension);
      Files.copy(source, copy);
      return copy;
    }
    Path output = target.resolveSibling(target.getFileName() + ".mp4");
    Path partial = target.resolveSibling(target.getFileName() + ".partial.mp4");
    var command =
        new ArrayList<>(
            List.of(
                "ffmpeg",
                "-nostdin",
                "-v",
                "error",
                "-n",
                "-i",
                source.toString(),
                "-map",
                "0:v:0?",
                "-map",
                "0:" + audioIndex,
                "-map_chapters",
                "-1",
                "-c:v",
                "copy"));
    if (delayed) command.addAll(List.of("-af", "aresample=async=1:first_pts=0"));
    boolean copyAudio = !delayed && Set.of("aac", "mp3").contains(chosen.codec());
    command.addAll(
        List.of(
            "-c:a",
            copyAudio ? "copy" : "aac",
            "-b:a",
            "192k",
            "-movflags",
            "+faststart",
            partial.toString()));
    try {
      run(command);
      Files.move(partial, output);
      return output;
    } finally {
      Files.deleteIfExists(partial);
    }
  }

  /**
   * Writes {@code target} as a JPEG of {@code image} (JPEG, PNG or WebP) at most {@code width}
   * pixels wide, keeping its aspect ratio; smaller images are not enlarged. The file appears
   * complete or not at all.
   */
  public void thumbnail(Path image, int width, Path target) throws IOException {
    Path partial = target.resolveSibling(target.getFileName() + ".partial.jpg");
    try {
      run(
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
      Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(partial);
    }
  }

  private static String text(JsonNode node, String field) {
    String value = node.path(field).asString("");
    return value.isBlank() ? null : value;
  }

  private static String extension(Path file) {
    String name = file.getFileName().toString();
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  /** Runs a tool to completion, returning stdout; a nonzero exit reports its stderr. */
  private static String run(List<String> command) throws IOException {
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(false).start();
    } catch (IOException missing) {
      throw new IOException(command.getFirst() + " is not installed or not on the PATH", missing);
    }
    var stderr = process.getErrorStream();
    var errors = new StringBuilder();
    var drain =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    errors.append(new String(stderr.readAllBytes(), StandardCharsets.UTF_8));
                  } catch (IOException ignored) {
                    // The exit status reports the failure.
                  }
                });
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    try {
      if (!process.waitFor(6, TimeUnit.HOURS)) process.destroyForcibly();
      drain.join();
    } catch (InterruptedException interrupted) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new IOException(command.getFirst() + " was interrupted", interrupted);
    }
    if (process.exitValue() != 0)
      throw new IOException(command.getFirst() + " failed: " + errors.toString().strip());
    return output;
  }
}
