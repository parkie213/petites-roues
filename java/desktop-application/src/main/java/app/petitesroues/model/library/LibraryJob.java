package app.petitesroues.model.library;

import app.petitesroues.library.progress.LibraryProgress;

/**
 * A snapshot of an Add or Refresh running in the background, as the library shows it.
 *
 * @param id identifies the job for following its progress
 * @param kind {@code add} or {@code refresh}
 * @param state {@code running}, {@code succeeded} or {@code failed}
 * @param progress the latest progress, or null before the first report
 * @param error why it failed, for the person to read; null unless failed
 */
public record LibraryJob(
    String id, String kind, String state, LibraryProgress progress, String error) {

  /** Whether the job has finished, successfully or not. */
  public boolean finished() {
    return !"running".equals(state);
  }
}
