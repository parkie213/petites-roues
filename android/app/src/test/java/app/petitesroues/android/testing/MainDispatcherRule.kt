package app.petitesroues.android.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Runs `Dispatchers.Main` (and so `viewModelScope`) on [dispatcher] for each test, so a test's
 * `runTest` scheduler drives ViewModel work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
  TestWatcher() {
  override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

  override fun finished(description: Description) = Dispatchers.resetMain()
}
