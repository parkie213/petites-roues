package com.petitesroues.android.library

import android.net.Uri
import com.petitesroues.android.Settings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the app reads and copies titles: the app's own folder ([AppFolder]) when it has file
 * access, together with the folders chosen in [Settings]. Each property is worked out when read, so
 * a grant or a newly chosen folder counts at once.
 */
@Singleton
class Folders
@Inject
constructor(private val settings: Settings, private val appFolder: AppFolder) {
  /** Every content folder the library reads: the app's own when it is allowed, then the chosen. */
  val library: List<Uri>
    get() = listOfNotNull(appFolder.ready()) + settings.contentTrees

  /**
   * Where titles from the desktop are copied: the chosen folder, else the app's own; null when
   * neither is usable.
   */
  val download: Uri?
    get() = settings.downloadFolder ?: appFolder.ready()
}
