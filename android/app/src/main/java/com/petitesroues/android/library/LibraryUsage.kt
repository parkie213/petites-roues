package com.petitesroues.android.library

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.File

/** The library header's disk usage line: the content folders' size and the space left. */
object LibraryUsage {
  /** "41.2 GB": decimal units, as Android's storage settings report sizes. */
  fun format(bytes: Long): String {
    val units = listOf("B", "kB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
      value /= 1000
      unit++
    }
    return if (unit == 0 || value >= 100) "%.0f %s".format(value, units[unit]) else "%.1f %s".format(value, units[unit])
  }

  /** "Library 4.1 GB · 18.3 GB free"; the size is left out until a refresh has measured it. */
  fun line(libraryBytes: Long?, freeBytes: List<Long>): String =
    (listOfNotNull(libraryBytes?.let { "Library ${format(it)}" }) + freeBytes.map { "${format(it)} free" }).joinToString(" · ")

  /**
   * Free bytes on each distinct storage volume holding one of [trees] (the shared storage, or a
   * removable card), found from the trees' document IDs ("primary:Sync", "1234-ABCD:Books").
   */
  fun free(context: Context, trees: List<Uri>): List<Long> {
    val volumes = context.getSystemService(StorageManager::class.java).storageVolumes
    return trees
      .mapNotNull { tree ->
        if (AppFolder.isFile(tree)) return@mapNotNull File(checkNotNull(tree.path))
        val volume = DocumentsContract.getTreeDocumentId(tree).substringBefore(':')
        if (volume == "primary") Environment.getExternalStorageDirectory()
        else volumes.firstOrNull { it.uuid == volume }?.directory
      }
      .distinctBy(File::getCanonicalPath)
      .map(File::getUsableSpace)
  }
}
