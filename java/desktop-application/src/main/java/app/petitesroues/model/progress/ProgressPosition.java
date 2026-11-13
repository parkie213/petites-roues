package app.petitesroues.model.progress;

/** Exactly one media-specific position, constructed after HTTP shape validation. */
public sealed interface ProgressPosition {
  record Video(Double positionSeconds) implements ProgressPosition {}

  record Comic(String pageId) implements ProgressPosition {}
}
