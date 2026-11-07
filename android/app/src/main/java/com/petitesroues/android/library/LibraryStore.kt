package com.petitesroues.android.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.net.toUri

/**
 * The phone's title index and reading/playback progress in a local SQLite database. Progress is
 * keyed by title path ([TitlePaths]), so it survives refreshes, and is never synced to the desktop.
 */
class LibraryStore(context: Context) :
  SQLiteOpenHelper(context, "library.sqlite", null, 6) {
  override fun onCreate(db: SQLiteDatabase) {
    createTitles(db)
    createFingerprints(db)
    db.execSQL(
      """CREATE TABLE progress (
           path TEXT PRIMARY KEY, position_seconds REAL, page_id TEXT,
           updated_at INTEGER NOT NULL, fraction REAL)"""
    )
  }

  /**
   * The title table is only an index of the content folders, so an upgrade empties it until the
   * next refresh. Progress is kept; version 3 adds how far through each title it is, version 4
   * indexes the reference duration in place of the edition, version 5 caches media
   * fingerprints, as titles link to media by fingerprint from any folder, and version 6 indexes
   * media without a document and the shelf, language and media path the shared shelves use.
   */
  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS title")
    createTitles(db)
    if (oldVersion < 5) createFingerprints(db)
    if (oldVersion < 3) db.execSQL("ALTER TABLE progress ADD COLUMN fraction REAL")
  }

  private fun createTitles(db: SQLiteDatabase) =
    db.execSQL(
      """CREATE TABLE title (
           path TEXT PRIMARY KEY, name TEXT NOT NULL, series TEXT, kind TEXT NOT NULL,
           document_uri TEXT, media_uri TEXT, document_id TEXT NOT NULL, duration_seconds REAL,
           season INTEGER, episode INTEGER, volume INTEGER, poster_uri TEXT,
           series_poster_uri TEXT, first_page TEXT, shelf TEXT, source_language TEXT,
           media_path TEXT)"""
    )

  /** A media file's fingerprint as of a refresh, with the size and time it was taken at. */
  data class Fingerprint(val size: Long, val modifiedMillis: Long, val fingerprint: String)

  /** Media fingerprints from the last refresh, keyed by document URI, reused while unchanged. */
  private fun createFingerprints(db: SQLiteDatabase) =
    db.execSQL(
      """CREATE TABLE fingerprint (
           uri TEXT PRIMARY KEY, size INTEGER NOT NULL, modified_millis INTEGER NOT NULL,
           fingerprint TEXT NOT NULL)"""
    )

  /** The cached fingerprints, keyed by media URI. */
  fun fingerprints(): Map<String, Fingerprint> =
    readableDatabase.rawQuery("SELECT uri, size, modified_millis, fingerprint FROM fingerprint", null).use {
      buildMap {
        while (it.moveToNext()) put(it.getString(0), Fingerprint(it.getLong(1), it.getLong(2), it.getString(3)))
      }
    }

  /** Replaces the fingerprint cache with one refresh's media, in one transaction. */
  fun replaceFingerprints(fingerprints: Map<String, Fingerprint>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      db.delete("fingerprint", null, null)
      for ((uri, cached) in fingerprints)
        db.insertOrThrow(
          "fingerprint",
          null,
          ContentValues().apply {
            put("uri", uri)
            put("size", cached.size)
            put("modified_millis", cached.modifiedMillis)
            put("fingerprint", cached.fingerprint)
          },
        )
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  /** Every indexed title, ordered by path. */
  fun titles(): List<Title> =
    readableDatabase
      .rawQuery(
        """SELECT path, name, series, kind, document_uri, media_uri, document_id, duration_seconds, season,
             episode, volume, poster_uri, series_poster_uri, first_page, shelf, source_language,
             media_path
           FROM title ORDER BY path""",
        null,
      )
      .use { cursor ->
        buildList {
          while (cursor.moveToNext())
            add(
              Title(
                path = cursor.getString(0),
                name = cursor.getString(1),
                series = cursor.getString(2),
                kind = TitleKind.valueOf(cursor.getString(3)),
                documentUri = cursor.getString(4)?.toUri(),
                mediaUri = cursor.getString(5)?.toUri(),
                documentId = cursor.getString(6),
                durationSeconds = if (cursor.isNull(7)) null else cursor.getDouble(7),
                season = cursor.int(8),
                episode = cursor.int(9),
                volume = cursor.int(10),
                poster = cursor.getString(11)?.toUri(),
                seriesPoster = cursor.getString(12)?.toUri(),
                firstPage = cursor.getString(13),
                shelf = cursor.getString(14),
                sourceLanguage = cursor.getString(15),
                mediaPath = cursor.getString(16),
              )
            )
        }
      }

  /** Replaces the whole index in one transaction; progress rows are kept. */
  fun replaceTitles(titles: List<Title>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      db.delete("title", null, null)
      for (title in titles)
        db.insertOrThrow(
          "title",
          null,
          ContentValues().apply {
            put("path", title.path)
            put("name", title.name)
            put("series", title.series)
            put("kind", title.kind.name)
            put("document_uri", title.documentUri?.toString())
            put("media_uri", title.mediaUri?.toString())
            put("document_id", title.documentId)
            put("duration_seconds", title.durationSeconds)
            put("season", title.season)
            put("episode", title.episode)
            put("volume", title.volume)
            put("poster_uri", title.poster?.toString())
            put("series_poster_uri", title.seriesPoster?.toString())
            put("first_page", title.firstPage)
            put("shelf", title.shelf)
            put("source_language", title.sourceLanguage)
            put("media_path", title.mediaPath)
          },
        )
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  /** Progress for every title that has any, keyed by path. */
  fun progress(): Map<String, Progress> =
    readableDatabase.rawQuery("SELECT path, position_seconds, page_id, fraction FROM progress", null).use {
      buildMap {
        while (it.moveToNext())
          put(
            it.getString(0),
            Progress(
              if (it.isNull(1)) null else it.getDouble(1),
              it.getString(2),
              if (it.isNull(3)) null else it.getDouble(3),
            ),
          )
      }
    }

  fun progress(path: String): Progress? = progress()[path]

  /** Saves a video position and, when the duration is known, how far through the video it is. */
  fun savePosition(path: String, seconds: Double, fraction: Double?) =
    save(path, "position_seconds" to seconds, "fraction" to fraction)

  /** Saves a comic page and how far through the comic it is, (page index + 1) / page count. */
  fun savePage(path: String, pageId: String, fraction: Double) =
    save(path, "page_id" to pageId, "fraction" to fraction)

  /** Forgets where a title was left: it opens at the start and its card shows no progress. */
  fun clearProgress(path: String) {
    writableDatabase.delete("progress", "path = ?", arrayOf(path))
  }

  private fun save(path: String, vararg columns: Pair<String, Any?>) {
    val values =
      ContentValues().apply {
        put("path", path)
        for ((column, value) in columns)
          when (value) {
            null -> putNull(column)
            is Double -> put(column, value)
            else -> put(column, value.toString())
          }
        put("updated_at", System.currentTimeMillis())
      }
    val db = writableDatabase
    if (db.update("progress", values, "path = ?", arrayOf(path)) == 0)
      db.insertOrThrow("progress", null, values)
  }
}

private fun Cursor.int(column: Int): Int? = if (isNull(column)) null else getInt(column)
