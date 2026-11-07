package com.petitesroues.android

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

/** Small per-device preferences: the content folders and the players' toggles. */
class Settings(context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

  /** The granted content folders, in the order they were added. */
  var contentTrees: List<Uri>
    get() =
      prefs.getString("contentTrees", null)?.lines()?.filter { it.isNotEmpty() }?.map { it.toUri() }
        ?: listOfNotNull(prefs.getString("contentTree", null)?.toUri())
    set(value) =
      prefs.edit {
        putString("contentTrees", value.joinToString("\n"))
        remove("contentTree")
      }

  var autoPause: Boolean
    get() = prefs.getBoolean("autoPause", false)
    set(value) = prefs.edit { putBoolean("autoPause", value) }

  var autoListen: Boolean
    get() = prefs.getBoolean("autoListen", false)
    set(value) = prefs.edit { putBoolean("autoListen", value) }

  var comicLandscape: Boolean
    get() = prefs.getBoolean("comicLandscape", false)
    set(value) = prefs.edit { putBoolean("comicLandscape", value) }

  /** Whether the comic reader outlines every bubble's region, as the browser's Show bubble outlines. */
  var comicOutlines: Boolean
    get() = prefs.getBoolean("comicOutlines", false)
    set(value) = prefs.edit { putBoolean("comicOutlines", value) }

  /**
   * Whether the comic at [path] reads right to left. Only asked of a comic without a study document,
   * which has no reading direction of its own; left to right until the reader flips it.
   */
  fun comicRtl(path: String): Boolean = prefs.getBoolean("comicRtl:$path", false)

  fun setComicRtl(path: String, rtl: Boolean) = prefs.edit { putBoolean("comicRtl:$path", rtl) }

  var showSubtitles: Boolean
    get() = prefs.getBoolean("showSubtitles", true)
    set(value) = prefs.edit { putBoolean("showSubtitles", value) }

  /** The quiz type last chosen in the Quiz me dialog (a [com.petitesroues.android.quiz.QuizPack.TYPES] id). */
  var quizType: String
    get() = prefs.getString("quizType", null) ?: "to-source"
    set(value) = prefs.edit { putString("quizType", value) }

  /** The key of the general quiz last shared from the library, or null before the first. */
  var generalQuiz: String?
    get() = prefs.getString("generalQuiz", null)
    set(value) = prefs.edit { putString("generalQuiz", value) }

  /** The content folders' total size from the last refresh, or null before one has measured it. */
  var libraryBytes: Long?
    get() = prefs.getLong("libraryBytes", -1).takeIf { it >= 0 }
    set(value) = prefs.edit { putLong("libraryBytes", value ?: -1) }

  var playbackSpeed: Float
    get() = prefs.getFloat("playbackSpeed", 1f)
    set(value) = prefs.edit { putFloat("playbackSpeed", value) }
}
