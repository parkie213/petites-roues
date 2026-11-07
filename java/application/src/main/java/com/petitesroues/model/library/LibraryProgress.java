package com.petitesroues.model.library;

/**
 * Where an Add or Refresh has got to, for showing progress while it runs.
 *
 * @param folder the registered folder being worked on, by name
 * @param phase {@code scanning} (walking the folder), {@code media} (fingerprinting and probing new
 *     or changed media; unchanged files are counted but not read), {@code documents} (reading study
 *     documents) or {@code saving} (updating the index)
 * @param done how many of {@code total} items the phase has finished; 0 when it has no count
 * @param total how many items the phase has; 0 when it has no count
 * @param current the file being worked on, relative to the folder, or null
 */
public record LibraryProgress(String folder, String phase, int done, int total, String current) {

  /** Receives progress as it happens. Called on the thread doing the work. */
  @FunctionalInterface
  public interface Listener {
    Listener NONE = progress -> {};

    void report(LibraryProgress progress);
  }
}
