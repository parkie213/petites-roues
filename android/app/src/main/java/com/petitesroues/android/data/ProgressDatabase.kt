package com.petitesroues.android.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * Where each title was left: the only data on the phone that a refresh cannot rebuild. Its schema
 * is exported to `app/schemas/`, one JSON file per version. Change it rarely; when it changes,
 * bump [version][Database.version] and declare the step, an `@AutoMigration` where Room can work
 * it out or a hand-written `Migration` where it can't (a rename), so progress survives app updates.
 */
@Database(entities = [Progress::class], version = 1, exportSchema = true)
abstract class ProgressDatabase : RoomDatabase() {
  abstract fun progress(): ProgressDao

  companion object {
    const val NAME = "progress.sqlite"
  }
}

/**
 * Where a title was left: a video position or a comic page, and how far through the title that is
 * from 0 to 1 ([fraction] is null for a video whose duration is not known). Keyed by the title's
 * document ID ([com.petitesroues.android.library.Title.progressKey]), which moving or renaming
 * files does not change.
 */
@Entity(tableName = "progress")
data class Progress(
  @PrimaryKey @ColumnInfo(name = "document_id") val documentId: String,
  @ColumnInfo(name = "position_seconds") val positionSeconds: Double? = null,
  @ColumnInfo(name = "page_id") val pageId: String? = null,
  val fraction: Double? = null,
  @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Progress reads and writes. They block, so that [ProgressStore] can run them one at a time in
 * order on its own thread; Room refuses them on the main thread.
 */
@Dao
interface ProgressDao {
  @Query("SELECT * FROM progress") fun all(): List<Progress>

  @Query("SELECT * FROM progress WHERE document_id = :documentId") fun find(documentId: String): Progress?

  @Upsert fun save(progress: Progress)

  @Query("DELETE FROM progress WHERE document_id = :documentId") fun clear(documentId: String)
}
