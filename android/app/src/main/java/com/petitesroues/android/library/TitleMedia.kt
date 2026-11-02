package com.petitesroues.android.library

/**
 * The desktop library's title-folder contract: a video document plays its folder's single video
 * (any filename), a comic document reads its folder's single CBZ. Missing media is allowed (the
 * title is listed but cannot open); more than one candidate is a problem to report.
 */
object TitleMedia {
  private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "ogv")

  /** The chosen sibling file name, or null when the folder has none. */
  fun select(siblings: List<String>, kind: TitleKind): String? {
    val candidates =
      siblings.filter {
        val extension = it.substringAfterLast('.', "").lowercase()
        if (kind == TitleKind.VIDEO) extension in videoExtensions else extension == "cbz"
      }
    require(candidates.size <= 1) {
      if (kind == TitleKind.VIDEO) "Multiple videos in title folder — keep one matching video"
      else "Multiple CBZ files in title folder — keep one edition"
    }
    return candidates.firstOrNull()
  }
}
