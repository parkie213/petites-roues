package com.petitesroues.android.library

/**
 * The phone's own part of indexing a title. Which media a document links to is the library's shared
 * rule ([com.petitesroues.library.linking.MediaLinks]); this only names titles.
 */
object TitlePaths {
  /**
   * A title's path, which identifies its library entry (progress is keyed by document ID instead):
   * its document's folder for a `document.json`, else the document's path without `.json`, so two
   * documents in one folder stay distinct. Prefixed with the content folder's name.
   */
  fun of(root: String, documentPath: String): String {
    val path =
      if (documentPath == "document.json") ""
      else if (documentPath.endsWith("/document.json")) documentPath.removeSuffix("/document.json")
      else documentPath.removeSuffix(".json")
    return if (path.isEmpty()) root else "$root/$path"
  }
}
