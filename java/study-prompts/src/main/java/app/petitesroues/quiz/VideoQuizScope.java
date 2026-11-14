package app.petitesroues.quiz;

import app.petitesroues.model.document.StudyDocument;
import app.petitesroues.model.document.VideoCue;
import app.petitesroues.model.document.VideoDocument;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Which lines of a video a quiz covers: the whole title or a time range. It owns the line
 * selection, the section heading, the file-name part, and the rule for which scope a quiz opens on,
 * so every platform selects and counts identically. Scopes are immutable and perform no I/O.
 */
public sealed interface VideoQuizScope {
  /** The scope choices a quiz menu offers. */
  enum Kind {
    WHOLE,
    RANGE
  }

  /** A title with more lines than this is flagged as large (a long brief for a chat). */
  int LARGE_LINES = 400;

  /** A title shorter than this many seconds opens on the whole title. */
  double SHORT_SECONDS = 900;

  /** The length, in seconds, of the range a long film opens on. */
  double RECENT_SECONDS = 600;

  /** Every subtitle line of the title. */
  record Whole() implements VideoQuizScope {
    @Override
    public Kind kind() {
      return Kind.WHOLE;
    }

    @Override
    public boolean includes(VideoCue cue) {
      return true;
    }

    @Override
    public String heading(List<VideoCue> selected) {
      return "Whole title";
    }

    @Override
    public String fileNamePart() {
      return "";
    }
  }

  /**
   * The lines overlapping the half-open range from {@code start} to {@code end} seconds, so a line
   * that only touches a boundary is excluded. Always finite with {@code 0 <= start < end}.
   */
  record TimeRange(double start, double end) implements VideoQuizScope {
    public TimeRange {
      if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || start >= end)
        throw new IllegalArgumentException("A time range needs finite 0 <= start < end");
    }

    @Override
    public Kind kind() {
      return Kind.RANGE;
    }

    @Override
    public boolean includes(VideoCue cue) {
      return cue.start() < end && cue.end() > start;
    }

    @Override
    public String heading(List<VideoCue> selected) {
      return "Lines in time range (" + QuizTime.format(start) + "–" + QuizTime.format(end) + ")";
    }

    @Override
    public String fileNamePart() {
      return QuizTime.format(start) + " to " + QuizTime.format(end);
    }
  }

  /**
   * What a quiz form holds before it is read: the chosen kind and the typed times, which only
   * matter for {@link Kind#RANGE}. Times are prefilled but may be edited into nonsense.
   */
  record Form(Kind kind, String start, String end) {}

  Kind kind();

  /** Whether {@code cue} (timed, with a line) belongs to this scope. */
  boolean includes(VideoCue cue);

  /**
   * The cues of {@code video} this scope covers, in playback order: only cues with a line whose
   * source text is not blank, so a quiz never asks about nothing.
   */
  default List<VideoCue> select(VideoDocument video) {
    return video.inPlaybackOrder().cues().stream()
        .filter(cue -> cue.hasLine() && !cue.line().source().isBlank() && includes(cue))
        .toList();
  }

  /** The brief's section heading for the selected cues: "Whole title" or the time range. */
  String heading(List<VideoCue> selected);

  /** Shown in the brief when the scope selects no lines. */
  default String emptyText() {
    return "No subtitle lines in this selection.";
  }

  /** The part of the file name after the title: empty for the whole title, else the times. */
  String fileNamePart();

  /**
   * The form a quiz opens on. It is {@link Kind#WHOLE} when the title is under {@link
   * #SHORT_SECONDS} long or is on a shelf other than Films (ignoring case), such as series and
   * comics; otherwise {@link Kind#RANGE}. The times are always prefilled: the range ends at {@code
   * position}, or at the first {@link #RECENT_SECONDS} of the title when the viewer has not begun
   * (position under a second), and starts {@link #RECENT_SECONDS} earlier, not before zero.
   *
   * @param position the playhead in seconds
   * @param playerSeconds the player's duration, or {@code null} when not known
   */
  static Form defaultFor(StudyDocument document, double position, Double playerSeconds) {
    double length = length(document, playerSeconds);
    boolean whole =
        length < SHORT_SECONDS
            || (document.shelf() != null && !document.shelf().equalsIgnoreCase("Films"));
    double end = position < 1 ? Math.min(RECENT_SECONDS, length) : position;
    double start = Math.max(0, end - RECENT_SECONDS);
    return new Form(whole ? Kind.WHOLE : Kind.RANGE, QuizTime.format(start), QuizTime.format(end));
  }

  /**
   * The title's length in seconds: the document's reference duration, else the player's duration,
   * else the end of its last cue, else zero. Only positive finite values count.
   */
  static double length(StudyDocument document, Double playerSeconds) {
    var video = document.video();
    var reference = video.reference();
    if (reference != null && positive(reference.durationSeconds()))
      return reference.durationSeconds();
    if (positive(playerSeconds)) return playerSeconds;
    return video.cues().stream()
        .map(VideoCue::end)
        .filter(VideoQuizScope::positive)
        .mapToDouble(Double::doubleValue)
        .max()
        .orElse(0);
  }

  /**
   * The scope a form means, or empty while a range's times are unreadable (see {@link
   * QuizTime#parse}) or its end is not after its start.
   */
  static Optional<VideoQuizScope> of(Form form) {
    if (form.kind() == Kind.WHOLE) return Optional.of(new Whole());
    OptionalDouble start = QuizTime.parse(form.start());
    OptionalDouble end = QuizTime.parse(form.end());
    if (start.isEmpty() || end.isEmpty() || end.getAsDouble() <= start.getAsDouble())
      return Optional.empty();
    return Optional.of(new TimeRange(start.getAsDouble(), end.getAsDouble()));
  }

  private static boolean positive(Double seconds) {
    return seconds != null && Double.isFinite(seconds) && seconds > 0;
  }
}
