package app.petitesroues.android.screens

import android.content.Context
import android.net.Uri
import androidx.room.Room
import app.petitesroues.android.data.ProgressDatabase
import app.petitesroues.android.data.ProgressStore
import app.petitesroues.android.library.Title
import app.petitesroues.android.library.TitleKind
import app.petitesroues.documents.StudyDocumentCodec
import app.petitesroues.documents.StudyDocumentReader
import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.library.MarkdownPageEntry
import app.petitesroues.model.library.Repository
import app.petitesroues.model.pages.MarkdownPage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The repository's tracked example titles (`example/`), opened as the phone opens them, for
 * screenshots of the reader and the player. Gradle passes the folder as the `examples` property.
 */
object Examples {
  private val root = File(System.getProperty("examples") ?: error("Run through Gradle"))

  /** An example folder (`documents-fr`, `media-fr`), as a content folder the library can read. */
  fun folder(name: String): Uri = Uri.fromFile(File(root, name))

  /** An example media file, as a file URI. */
  fun media(path: String): Uri = Uri.fromFile(File(root, "media-fr/$path"))

  /** An example note or quiz ([folder] is `notes` or `quizzes`), as the page scanner reads it. */
  fun page(folder: String, name: String): MarkdownPageEntry {
    val text = File(root, "documents-fr/$folder/$name.md").readText()
    return MarkdownPageEntry(
      Repository("example", "example"),
      "$folder/$name.md",
      MarkdownPage(MarkdownPage.title(text, name), text),
    )
  }

  fun document(path: String): StudyDocument =
    StudyDocumentReader(StudyDocumentCodec())
      .read(File(root, "documents-fr/$path/document.json").toPath())

  fun title(path: String, media: String, kind: TitleKind, document: StudyDocument) =
    Title(
      path = path,
      name = document.title(),
      series = null,
      kind = kind,
      documentUri = Uri.fromFile(File(root, "documents-fr/$path/document.json")),
      mediaUri = Uri.fromFile(File(root, "media-fr/$media")),
      documentId = document.id(),
      durationSeconds = null,
      season = null,
      episode = null,
      volume = null,
      poster = null,
      seriesPoster = null,
      firstPage = null,
      sourceLanguage = document.sourceLanguage(),
      mediaPath = media,
    )

  /** A progress store over an empty in-memory database. */
  fun progress(context: Context): ProgressStore {
    val database =
      Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    return ProgressStore(database.progress(), CoroutineScope(Dispatchers.Unconfined))
  }
}
