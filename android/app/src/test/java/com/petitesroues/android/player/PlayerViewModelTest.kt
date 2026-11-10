package com.petitesroues.android.player

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petitesroues.android.Settings
import com.petitesroues.android.data.Progress
import com.petitesroues.android.data.ProgressDatabase
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.library.TitleKind
import com.petitesroues.android.screens.Examples
import com.petitesroues.android.screens.SDK
import com.petitesroues.android.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The player's behaviour without drawing it: a real ExoPlayer on the example episode (Robolectric
 * reads the file but doesn't decode it), real settings and a progress database in memory.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [SDK])
class PlayerViewModelTest {
  @get:Rule val main = MainDispatcherRule()

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val settings = Settings(context)
  private val database =
    Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  private val progress = ProgressStore(database.progress(), CoroutineScope(Dispatchers.Unconfined))
  private val document = Examples.document(PEPPA)
  private val title =
    Examples.title(
      PEPPA,
      "series/peppa-pig/s01e01-les-flaques-de-boue.mp4",
      TitleKind.VIDEO,
      document,
    )

  // Held as the title's back-stack entry holds it, so clearing it is closing the title.
  private val store = ViewModelStore()

  @After fun tearDown() = store.clear().also { database.close() }

  private fun open(saved: Progress? = null): PlayerViewModel =
    ViewModelProvider.create(
        store,
        viewModelFactory {
          initializer { PlayerViewModel(title, document, saved, context, settings, progress) }
        },
      )[PlayerViewModel::class]

  @Test
  fun `opening resumes where the title was left`() {
    val model = open(Progress(title.progressKey, positionSeconds = 12.5))

    assertEquals(12.5, model.playback.position, 0.001)
  }

  @Test
  fun `closing the title saves where it was`() = runTest {
    val model = open()
    model.playback.seek(20.0)

    store.clear()

    assertEquals(20.0, progress.find(title.progressKey)?.positionSeconds)
  }

  @Test
  fun `the remembered choices are read when it opens`() {
    settings.autoPause = true
    settings.playbackSpeed = 0.8f
    settings.controlsOnLeft = true

    val model = open()

    assertEquals(PlayerUiState(true, 0.8f, true, true), model.state.value)
    assertTrue(model.playback.autoPause)
    assertEquals(0.8f, model.playback.player.playbackParameters.speed)
  }

  @Test
  fun `a new speed plays at once and is remembered`() {
    val model = open()

    model.setSpeed(0.6f)

    assertEquals(0.6f, model.state.value.speed)
    assertEquals(0.6f, model.playback.player.playbackParameters.speed)
    assertEquals(0.6f, settings.playbackSpeed)
  }

  @Test
  fun `auto-pause, subtitles and the controls' side are remembered`() {
    val model = open()

    model.setAutoPause(true)
    model.toggleSubtitles()
    model.swapSides()

    assertEquals(PlayerUiState(true, 1f, false, true), model.state.value)
    assertTrue(model.playback.autoPause)
    assertTrue(settings.autoPause)
    assertFalse(settings.showSubtitles)
    assertTrue(settings.controlsOnLeft)
  }

  @Test
  fun `the quiz type shared is offered next time`() {
    open().quizShared("to-translation")

    assertEquals("to-translation", open().quizType)
  }

  private companion object {
    const val PEPPA = "series/peppa-pig/s01e01"
  }
}
