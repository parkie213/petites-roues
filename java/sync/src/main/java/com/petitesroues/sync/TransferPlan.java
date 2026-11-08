package com.petitesroues.sync;

import com.petitesroues.media.MediaFingerprint;
import com.petitesroues.model.sync.DesktopTitle;
import com.petitesroues.model.sync.TitleFile;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * What copying some desktop titles into a download folder still involves. A media file or poster
 * that is already there whole is skipped: same size and, for media, the same fingerprint. Documents
 * are always fetched, since the desktop serves them with pending edits replayed and they are small.
 * A part left by an earlier attempt counts as already downloaded.
 *
 * @param steps the files to fetch, in order: title by title, media first and the document last
 * @param remaining bytes still to download, the estimate the free-space check uses
 */
public record TransferPlan(List<Step> steps, long remaining) {
  /**
   * One file to fetch.
   *
   * @param title the title it belongs to
   * @param file the file
   * @param last whether it is the title's last file, after which the title is complete
   */
  public record Step(DesktopTitle title, TitleFile file, boolean last) {}

  /**
   * The plan for copying {@code titles} into {@code folder}; titles with a problem are left out.
   *
   * @throws IllegalArgumentException if the desktop names a path outside the folder
   */
  public static TransferPlan of(List<DesktopTitle> titles, TransferFolder folder)
      throws IOException {
    List<Step> steps = new ArrayList<>();
    long remaining = 0;
    for (DesktopTitle title : titles) {
      if (title.problem() != null) continue;
      List<TitleFile> needed = new ArrayList<>();
      for (TitleFile file : title.files()) {
        if (!file.document() && present(file, folder)) continue;
        needed.add(file);
        long part = file.document() ? 0 : Math.max(0, folder.partSize(file.segments()));
        remaining += Math.max(0, file.bytes() - part);
      }
      for (int i = 0; i < needed.size(); i++)
        steps.add(new Step(title, needed.get(i), i == needed.size() - 1));
    }
    return new TransferPlan(List.copyOf(steps), remaining);
  }

  /** Whether the finished file is already there and the same as the desktop's. */
  static boolean present(TitleFile file, TransferFolder folder) throws IOException {
    var path = file.segments();
    if (folder.size(path) != file.bytes()) return false;
    if (file.fingerprint() == null) return true;
    try (SeekableByteChannel channel = folder.open(path)) {
      return file.fingerprint().equals(MediaFingerprint.of(channel));
    }
  }

  /** The titles this plan completes, in order. */
  public List<DesktopTitle> titles() {
    return steps.stream().filter(Step::last).map(Step::title).toList();
  }
}
