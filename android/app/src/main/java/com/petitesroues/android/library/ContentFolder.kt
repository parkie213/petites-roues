package com.petitesroues.android.library

import android.net.Uri
import com.petitesroues.library.discovery.ContentTree

/**
 * A content folder as the library scans it: a SAF grant ([SafContentTree]) or the app's own
 * ([FileContentTree]).
 */
interface ContentFolder : ContentTree<Uri> {
  /** The folder's own name, which keys its titles. */
  fun name(): String
}
