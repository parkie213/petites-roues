package com.petitesroues.android

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso
import com.petitesroues.android.data.Progress
import com.petitesroues.android.data.ProgressDao
import com.petitesroues.android.screens.Examples
import com.petitesroues.android.screens.PHONE
import com.petitesroues.android.screens.SDK
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment

/**
 * The app as a whole, under Robolectric with Hilt: the library refreshes the repository's
 * `example/` folders, and its screens are reached and left through the back stack.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [SDK], qualifiers = PHONE)
class NavigationTest {
  @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

  /** Points the library at the examples before the activity starts. */
  @get:Rule(order = 1)
  val folders =
    object : ExternalResource() {
      override fun before() {
        ShadowEnvironment.addExternalDir("storage")
        Settings(RuntimeEnvironment.getApplication()).contentTrees =
          listOf(Examples.folder("documents-fr"), Examples.folder("media-fr"))
      }
    }

  @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

  @Inject lateinit var progress: ProgressDao

  @Before
  fun setUp() {
    hilt.inject()
  }

  /** Waits for a node matching [matcher], printing the screen if it never comes. */
  private fun await(matcher: SemanticsMatcher) =
    try {
      compose.waitUntil(timeoutMillis = 15_000) {
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
      }
    } catch (timeout: ComposeTimeoutException) {
      throw AssertionError(compose.onRoot().printToString(), timeout)
    }

  /** Taps the library card named [name]. */
  private fun tapCard(name: String) {
    await(hasContentDescription(name) and hasClickAction())
    compose.onNode(hasContentDescription(name) and hasClickAction()).performClick()
  }

  /** Opens the example comic from its series card in the library, on its saved page. */
  private fun openComic() {
    progress.save(Progress("lulu-example-le-parapluie", pageId = "page-1"))
    tapCard(SERIES)
    tapCard(LULU)
    await(hasContentDescription("1"))
  }

  @Test
  fun `a title opens over the library, and back returns to the series it was opened from`() {
    openComic()
    compose.onNodeWithText("1 tome").assertDoesNotExist()

    Espresso.pressBack()
    await(hasContentDescription(LULU))
    compose.onNodeWithText("Library").assertDoesNotExist()
  }

  @Test
  fun `the open title survives the activity being recreated`() {
    openComic()

    compose.activityRule.scenario.recreate()
    await(hasContentDescription("1"))
    compose.onNodeWithContentDescription(LULU).assertDoesNotExist()
  }

  private companion object {
    const val SERIES = "Lulu"
    const val LULU = "Le parapluie de Lulu"
  }
}
