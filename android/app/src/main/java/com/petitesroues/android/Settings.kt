package com.petitesroues.android

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import com.petitesroues.android.comic.ListeningMode
import com.petitesroues.android.library.AppFolder
import com.petitesroues.model.sync.Desktop
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small per-device preferences: the content folders, the paired desktop and the players' toggles. */
@Singleton
class Settings @Inject constructor(@ApplicationContext context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

  /** The granted content folders, in the order they were added. */
  var contentTrees: List<Uri>
    get() = prefs.getString("contentTrees", null)?.lines()?.filter { it.isNotEmpty() }?.map { it.toUri() }.orEmpty()
    set(value) = prefs.edit { putString("contentTrees", value.joinToString("\n")) }

  /**
   * Every content folder the library reads: the app's own ([AppFolder]) when it is allowed, then
   * the chosen ones.
   */
  val libraryFolders: List<Uri>
    get() = listOfNotNull(AppFolder.ready()) + contentTrees

  /** The desktop this phone is paired with, or null. Its token is the phone's credential there. */
  var desktop: Desktop?
    get() {
      val url = prefs.getString("desktopUrl", null) ?: return null
      val token = prefs.getString("desktopToken", null) ?: return null
      val pin = prefs.getString("desktopPin", null) ?: return null
      return Desktop(prefs.getString("desktopName", null) ?: "Desktop", url, token, pin)
    }
    set(value) =
      prefs.edit {
        if (value == null) {
          remove("desktopUrl")
          remove("desktopToken")
          remove("desktopName")
          remove("desktopPin")
        } else {
          putString("desktopUrl", value.url())
          putString("desktopToken", value.token())
          putString("desktopName", value.name())
          putString("desktopPin", value.pin())
        }
      }

  /**
   * A folder chosen to copy titles from the desktop into instead of the app's own ([AppFolder]),
   * granted for writing and also one of the content folders; null to use the app's own.
   */
  var downloadFolder: Uri?
    get() = prefs.getString("downloadFolder", null)?.toUri()
    set(value) = prefs.edit { if (value == null) remove("downloadFolder") else putString("downloadFolder", value.toString()) }

  /**
   * The desktop's series and standalone titles this phone follows, by
   * [com.petitesroues.model.sync.DesktopTitle.group]: syncing fetches their new titles as well as
   * updates to what is already on the phone.
   */
  var followed: Set<String>
    get() = prefs.getStringSet("followed", null)?.toSet().orEmpty()
    set(value) = prefs.edit { putStringSet("followed", value) }

  /**
   * Desktop titles chosen one by one, by ID, still waiting to be copied. Once a title is on the
   * phone it gets updates like any other, so [com.petitesroues.android.desktop.Transfers.check]
   * drops it from here.
   */
  var chosen: Set<String>
    get() = prefs.getStringSet("chosen", null)?.toSet().orEmpty()
    set(value) = prefs.edit { putStringSet("chosen", value) }

  var autoPause: Boolean
    get() = prefs.getBoolean("autoPause", false)
    set(value) = prefs.edit { putBoolean("autoPause", value) }

  /** What tapping a bubble does in the comic reader. */
  var listeningMode: ListeningMode
    get() = ListeningMode.entries.firstOrNull { it.name == prefs.getString("listeningMode", null) } ?: ListeningMode.OFF
    set(value) = prefs.edit { putString("listeningMode", value.name) }

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
