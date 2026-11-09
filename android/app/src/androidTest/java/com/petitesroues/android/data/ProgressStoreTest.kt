package com.petitesroues.android.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressStoreTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val database = Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java).build()
  private val scope = CoroutineScope(SupervisorJob())
  private val store = ProgressStore(database.progress(), scope)

  @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ProgressDatabase::class.java)

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun theLastSaveWinsAndReadsSeeEarlierSaves() = runBlocking {
    for (second in 1..50) store.savePosition("robots", second.toDouble(), second / 100.0)
    val saved = store.find("robots")!!
    assertEquals(50.0, saved.positionSeconds!!, 0.0)
    assertEquals(0.5, saved.fraction!!, 0.0)
  }

  @Test
  fun comicPagesAndClearing() = runBlocking {
    store.savePage("naruto-t01", "page-012", 0.25)
    store.savePosition("robots", 30.0, null)
    assertEquals(setOf("naruto-t01", "robots"), store.all().keys)
    assertEquals("page-012", store.find("naruto-t01")!!.pageId)
    store.clear("naruto-t01")
    assertNull(store.find("naruto-t01"))
    assertEquals(setOf("robots"), store.all().keys)
  }

  /** The exported schema is the one Room builds, so later versions can migrate from it. */
  @Test
  fun exportedSchemaMatches() {
    migrations.createDatabase("schema-check", 1).close()
    migrations.runMigrationsAndValidate("schema-check", 1, true).close()
  }
}
