package app.petitesroues.model.progress;

/** Desktop resume-position snapshot with an ISO timestamp; unsaved snapshots have no timestamp. */
public record PlaybackProgress(double positionSeconds, String updatedAt) implements SavedProgress {}
