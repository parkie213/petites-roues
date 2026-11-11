package com.petitesroues.android.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import java.io.File
import javax.inject.Inject

/**
 * The app's own content folder, `Internal storage/Petites roues`: always a content folder, where
 * titles copied from the desktop go unless another folder is chosen, and where titles copied any
 * other way (USB, Syncthing) are found too. Android only lets the app create and write a folder
 * there with All files access, which the person grants once in system settings. The folder is
 * reached with plain file access, so its handles are `file:` URIs ([FileContentTree]); it stays
 * when the app is uninstalled. [AndroidAppFolder] is the real one; tests use a temporary folder.
 */
interface AppFolder {
  /** The folder, created if needed; null without All files access. */
  fun ready(): Uri?

  companion object {
    const val LABEL = "Internal storage/Petites roues"

    /**
     * Whether [tree] is a folder reached with file access, as this one is, rather than a SAF grant.
     */
    fun isFile(tree: Uri): Boolean = tree.scheme == ContentResolver.SCHEME_FILE

    /** The system settings page where the person grants this app All files access. */
    fun allowIntent(context: Context): Intent =
      Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:${context.packageName}".toUri(),
      )
  }
}

/**
 * [AppFolder] in the phone's shared storage. Asks each time whether All files access is granted,
 * since the person can grant or revoke it in system settings while the app runs.
 */
class AndroidAppFolder @Inject constructor() : AppFolder {
  override fun ready(): Uri? {
    if (!Environment.isExternalStorageManager()) return null
    val dir = File(Environment.getExternalStorageDirectory(), "Petites roues")
    return if (dir.isDirectory || dir.mkdirs()) Uri.fromFile(dir) else null
  }
}
