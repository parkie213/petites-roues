package app.petitesroues.android.library

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.petitesroues.android.Settings
import app.petitesroues.android.screens.SDK
import app.petitesroues.android.testing.FakeAppFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which folders the library reads and where titles are copied, with and without file access. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class FoldersTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val own = Uri.parse("file:///storage/emulated/0/Petites%20roues")
  private val films =
    Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AFilms")
  private val appFolder = FakeAppFolder()
  private lateinit var settings: Settings
  private lateinit var folders: Folders

  @Before
  fun setUp() {
    settings = Settings(context).apply { contentTrees = listOf(films) }
    folders = Folders(settings, appFolder)
  }

  @Test
  fun `the library reads the app's own folder first, once it is allowed`() {
    assertEquals(listOf(films), folders.library)
    appFolder.folder = own
    assertEquals(listOf(own, films), folders.library)
  }

  @Test
  fun `titles are copied into the chosen folder, else the app's own`() {
    appFolder.folder = own
    assertEquals(own, folders.download)
    settings.downloadFolder = films
    assertEquals(films, folders.download)
  }

  @Test
  fun `without file access or a chosen folder there is nowhere to copy`() {
    assertNull(folders.download)
  }
}
