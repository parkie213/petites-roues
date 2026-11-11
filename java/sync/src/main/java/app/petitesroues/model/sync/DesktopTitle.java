package app.petitesroues.model.sync;

import java.util.List;

/**
 * A title the desktop offers to phones: its study document and the files it needs. The desktop's
 * notes and quizzes are offered the same way, as one entry of kind {@link #PAGES}.
 *
 * @param id the desktop's title ID, or {@link #PAGES_ID}
 * @param title its display title
 * @param series its series, or null
 * @param kind {@code video}, {@code comic} or {@link #PAGES}
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
  /** The kind of the entry holding the desktop's notes and quizzes. */
  public static final String PAGES = "pages";

  /** The ID of the entry holding the desktop's notes and quizzes. */
  public static final String PAGES_ID = "notes-and-quizzes";

  /**
   * What a phone follows this title by: its series, or its own ID when it stands alone. Following a
   * group fetches every title in it, including ones the desktop adds later.
   */
  public String group() {
    return series != null ? series : id;
  }

  /** The files' total size in bytes. */
  public long bytes() {
    return files.stream().mapToLong(TitleFile::bytes).sum();
  }
}
