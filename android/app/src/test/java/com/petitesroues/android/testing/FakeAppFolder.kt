package com.petitesroues.android.testing

import android.net.Uri
import com.petitesroues.android.library.AppFolder

/**
 * The app's own folder for tests: [folder], or null as on a phone without All files access. Set
 * [folder] to grant or revoke access while a test runs.
 */
class FakeAppFolder(var folder: Uri? = null) : AppFolder {
  override fun ready(): Uri? = folder
}
