package app.petitesroues.android.data

import app.petitesroues.android.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saves and reads reading and playback progress for the player and the comic reader. Saves are
 * fire-and-forget, so a screen can save as it closes: they run in the application's scope, one at a
 * time in the order they were made, so the last save made is the one kept. Reads wait for saves
 * already queued.
 */
@Singleton
class ProgressStore
@Inject
constructor(private val dao: ProgressDao, @ApplicationScope private val scope: CoroutineScope) {
  /** One call at a time, in submission order; the DAO's calls block, so none overtakes another. */
  private val writes = Dispatchers.IO.limitedParallelism(1)

  /** Progress for every title that has any, keyed by document ID. */
  suspend fun all(): Map<String, Progress> =
    withContext(writes) { dao.all().associateBy { it.documentId } }

  /** Where the title with this document ID was left, or null. */
  suspend fun find(documentId: String): Progress? = withContext(writes) { dao.find(documentId) }

  /** Saves a video position and, when the duration is known, how far through the video it is. */
  fun savePosition(documentId: String, seconds: Double, fraction: Double?) =
    save(Progress(documentId, positionSeconds = seconds, fraction = fraction))

  /** Saves a comic page and how far through the comic it is, (page index + 1) / page count. */
  fun savePage(documentId: String, pageId: String, fraction: Double) =
    save(Progress(documentId, pageId = pageId, fraction = fraction))

  /** Forgets where a title was left: it opens at the start and its card shows no progress. */
  suspend fun clear(documentId: String) = withContext(writes) { dao.clear(documentId) }

  private fun save(progress: Progress) {
    scope.launch(writes) { dao.save(progress) }
  }
}
