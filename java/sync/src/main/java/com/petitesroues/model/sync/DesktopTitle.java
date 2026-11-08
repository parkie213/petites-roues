package com.petitesroues.model.sync;

import java.util.List;

/**
 * A title the desktop offers to phones: its study document and the files it needs.
 *
 * @param id the desktop's title ID
 * @param title its display title
 * @param series its series, or null
 * @param kind {@code video} or {@code comic}
 * @param season season number, or null
 * @param episode episode number, or null
 * @param volume volume number, or null
 * @param problem why it cannot be copied to a phone yet (no media matches its reference, or the
 *     media is linked by hand, which the phone does not follow), or null when it can
 * @param files what to download, media first and the document last, so a title only appears on the
 *     phone once it is whole
 */
public record DesktopTitle(
    String id,
    String title,
    String series,
    String kind,
    Integer season,
    Integer episode,
    Integer volume,
    String problem,
    List<TitleFile> files) {
  /** The files' total size in bytes. */
  public long bytes() {
    return files.stream().mapToLong(TitleFile::bytes).sum();
  }
}
