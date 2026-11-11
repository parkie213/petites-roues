package app.petitesroues.model.progress;

/** A finite nonnegative video resume position in seconds, independent of storage and timestamps. */
public record PlaybackPosition(double seconds) {
  public PlaybackPosition {
    if (!Double.isFinite(seconds) || seconds < 0)
      throw new IllegalArgumentException("Playback position must be finite and nonnegative");
  }

  /** Fraction from zero to one; unknown, nonpositive or nonfinite durations have no fraction. */
  public Double fraction(Double durationSeconds) {
    return durationSeconds == null || !Double.isFinite(durationSeconds) || durationSeconds <= 0
        ? null
        : Math.min(1, seconds / durationSeconds);
  }
}
