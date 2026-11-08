package com.petitesroues.android.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * The app's own content folder, `Internal storage/Petites roues`: always a content folder, where
 * titles copied from the desktop go unless another folder is chosen, and where titles copied any
 * other way (USB, Syncthing) are found too. Android only lets the app create and write a folder
 * there with All files access, which the person grants once in system settings. The folder is
 * reached with plain file access, so its handles are `file:` URIs ([FileContentTree]); it stays
 * when the app is uninstalled.
 */
object AppFolder {
  const val LABEL = "Internal storage/Petites roues"

  private val dir: File
    get() = File(Environment.getExternalStorageDirectory(), "Petites roues")

  /** Whether the app has All files access. */
  fun allowed(): Boolean = Environment.isExternalStorageManager()

  /** The folder, created if needed; null without All files access. */
  fun ready(): Uri? = if (allowed() && (dir.isDirectory || dir.mkdirs())) Uri.fromFile(dir) else null

  /** Whether [tree] is a folder reached with file access, as this one is, rather than a SAF grant. */
  fun isFile(tree: Uri): Boolean = tree.scheme == ContentResolver.SCHEME_FILE

  /** The system settings page where the person grants this app All files access. */
  fun allowIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
}
