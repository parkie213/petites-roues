package app.petitesroues.model.library;

import app.petitesroues.library.discovery.MediaKind;
import app.petitesroues.media.AudioLanguages;
import app.petitesroues.model.library.media.MediaEntry;
import java.nio.file.Path;
import java.util.List;

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
 * @param audioPlayable whether the first audio stream, the one browsers play, is browser-playable;
 *     null for a comic or when unknown
 * @param audioLanguages each audio stream's language in track order, comma-separated BCP 47 primary
 *     tags with an untagged stream empty ({@link AudioLanguages}); null for a comic or when unknown
 * @param posterPath its own card image, relative to the folder, or null ({@link
 *     app.petitesroues.library.discovery.ContentScanner} finds posters at Refresh)
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
    String audioLanguages,
    String posterPath,
    String seriesPosterPath,
    String posterProblem) {

  /** Shared file identity and matching metadata, without local paths or probe-cache state. */
  public MediaEntry repositoryEntry() {
    return new MediaEntry(
        repositoryId,
        relativePath,
        "comic".equals(kind) ? MediaKind.COMIC : MediaKind.VIDEO,
        fingerprint);
  }

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

  /** The language of the first audio track, the one browsers play, or null when untagged. */
  public String playedLanguage() {
    var languages = AudioLanguages.split(audioLanguages);
    return languages.isEmpty() || languages.getFirst().isEmpty() ? null : languages.getFirst();
  }

  /**
   * The warning for a video whose document is in {@code sourceLanguage} but whose first audio
   * track, the one browsers play, is tagged with another language; null when they agree or either
   * is unknown.
   */
  public String languageWarning(String sourceLanguage) {
    String played = playedLanguage();
    if (!"video".equals(kind) || played == null || sourceLanguage == null) return null;
    String wanted = AudioLanguages.tag(sourceLanguage.split("-")[0]);
    if (played.equals(wanted)) return null;
    boolean has = AudioLanguages.split(audioLanguages).contains(wanted);
    return "Plays "
        + AudioLanguages.name(played)
        + " audio, not "
        + AudioLanguages.name(wanted)
        + (has
            ? ": make the " + AudioLanguages.name(wanted) + " track the first"
            : ": it has no " + AudioLanguages.name(wanted) + " track");
  }

  /**
   * For a video with audio in several languages, which one browsers play ("Plays English audio of
   * English, French and Dutch"); null otherwise.
   */
  public String languagesNote() {
    if (!"video".equals(kind)) return null;
    List<String> distinct =
        AudioLanguages.split(audioLanguages).stream()
            .filter(language -> !language.isEmpty())
            .distinct()
            .toList();
    String played = playedLanguage();
    if (distinct.size() < 2 || played == null) return null;
    var names = distinct.stream().map(AudioLanguages::name).toList();
    String list = String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    return "Plays " + AudioLanguages.name(played) + " audio of " + list;
  }

  /** Whether this is a video whose audio no browser can play. */
  public boolean unplayableAudio() {
    return "video".equals(kind) && Boolean.FALSE.equals(audioPlayable);
  }

  /** The warning for {@link #unplayableAudio()}, naming the codecs found. */
  public String audioWarning() {
    if (audioCodecs == null || audioCodecs.isBlank()) return "No audio track";
    // Codecs are listed in track order, so the first is the first track's.
    String first = audioCodecs.split(",")[0].toUpperCase();
    return audioCodecs.contains(",")
        ? "The first audio track (" + first + ") won't play in a browser"
        : "No audio track this player can play (" + first + ")";
  }
}
