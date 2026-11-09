package com.petitesroues.android.data

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.petitesroues.android.library.Title
import com.petitesroues.android.library.TitleKind

/**
 * The library index: every title the last refresh found, and the media fingerprints it computed.
 * Both are rebuilt from the content folders by each refresh, so this database is a cache. It has
 * no migrations: when its schema changes, Room drops and recreates it (see
 * [com.petitesroues.android.di.DataModule]) and the library refreshes because the index is empty.
 * Reading and playback progress is kept apart, in [ProgressDatabase].
 */
@Database(entities = [IndexedTitle::class, CachedFingerprint::class], version = 1, exportSchema = false)
@TypeConverters(UriConverter::class)
abstract class IndexDatabase : RoomDatabase() {
  abstract fun index(): IndexDao

  companion object {
    const val NAME = "index.sqlite"
  }
}

/** One [Title] as the index stores it. */
@Entity(tableName = "title")
data class IndexedTitle(
  @PrimaryKey val path: String,
  val name: String,
  val series: String?,
  val kind: TitleKind,
  val documentUri: Uri?,
  val mediaUri: Uri?,
  val documentId: String,
  val durationSeconds: Double?,
  val season: Int?,
  val episode: Int?,
  val volume: Int?,
  val poster: Uri?,
  val seriesPoster: Uri?,
  val firstPage: String?,
  val shelf: String?,
  val sourceLanguage: String?,
  val mediaPath: String?,
) {
  fun toTitle() =
    Title(
      path = path,
      name = name,
      series = series,
      kind = kind,
      documentUri = documentUri,
      mediaUri = mediaUri,
      documentId = documentId,
      durationSeconds = durationSeconds,
      season = season,
      episode = episode,
      volume = volume,
      poster = poster,
      seriesPoster = seriesPoster,
      firstPage = firstPage,
      shelf = shelf,
      sourceLanguage = sourceLanguage,
      mediaPath = mediaPath,
    )

  companion object {
    fun of(title: Title) =
      with(title) {
        IndexedTitle(
        path = path,
        name = name,
        series = series,
        kind = kind,
        documentUri = documentUri,
        mediaUri = mediaUri,
        documentId = documentId,
        durationSeconds = durationSeconds,
        season = season,
        episode = episode,
        volume = volume,
        poster = poster,
        seriesPoster = seriesPoster,
        firstPage = firstPage,
        shelf = shelf,
        sourceLanguage = sourceLanguage,
        mediaPath = mediaPath,
        )
      }
  }
}

/**
 * A media file's fingerprint as of a refresh, with the size and modification time it was taken
 * at, so an unchanged file is not read again.
 */
@Entity(tableName = "fingerprint")
data class CachedFingerprint(
  @PrimaryKey val uri: String,
  val size: Long,
  val modifiedMillis: Long,
  val fingerprint: String,
)

/**
 * Reads and replaces the index. Its calls block, and Room refuses them on the main thread: the
 * refresh and the library's reload run on [kotlinx.coroutines.Dispatchers.IO].
 */
@Dao
abstract class IndexDao {
  /** Every indexed title, ordered by path. */
  fun titles(): List<Title> = allTitles().map(IndexedTitle::toTitle)

  /** Replaces the whole index with one refresh's titles, all or nothing. */
  @Transaction
  open fun replaceTitles(titles: List<Title>) {
    deleteTitles()
    insertTitles(titles.map(IndexedTitle::of))
  }

  /** The cached fingerprints, keyed by media URI. */
  fun fingerprints(): Map<String, CachedFingerprint> = allFingerprints().associateBy { it.uri }

  /** Replaces the fingerprint cache with one refresh's media, all or nothing. */
  @Transaction
  open fun replaceFingerprints(fingerprints: Collection<CachedFingerprint>) {
    deleteFingerprints()
    insertFingerprints(fingerprints)
  }

  @Query("SELECT * FROM title ORDER BY path") protected abstract fun allTitles(): List<IndexedTitle>

  @Query("DELETE FROM title") protected abstract fun deleteTitles()

  @Insert protected abstract fun insertTitles(titles: List<IndexedTitle>)

  @Query("SELECT * FROM fingerprint") protected abstract fun allFingerprints(): List<CachedFingerprint>

  @Query("DELETE FROM fingerprint") protected abstract fun deleteFingerprints()

  @Insert protected abstract fun insertFingerprints(fingerprints: Collection<CachedFingerprint>)
}

/** Stores content URIs as their strings. */
class UriConverter {
  @TypeConverter fun toText(uri: Uri?): String? = uri?.toString()

  @TypeConverter fun toUri(text: String?): Uri? = text?.toUri()
}
