package com.petitesroues.android.screens

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.petitesroues.android.data.ProgressDatabase
import com.petitesroues.android.data.ProgressStore
import com.petitesroues.android.library.Title
import com.petitesroues.android.library.TitleKind
import com.petitesroues.android.pages.MarkdownPage
import com.petitesroues.documents.StudyDocumentReader
import com.petitesroues.model.document.StudyDocument
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The repository's tracked example titles (`example/`), opened as the phone opens them, for
 * screenshots of the reader and the player. Gradle passes the folder as the `examples` property.
 */
object Examples {
  private val root = File(System.getProperty("examples") ?: error("Run through Gradle"))

  /** An example media file, as a file URI. */
  fun media(path: String): Uri = Uri.fromFile(File(root, "media-fr/$path"))

  /** An example note or quiz ([folder] is `notes` or `quizzes`), as the page scanner reads it. */
  fun page(folder: String, name: String): MarkdownPage {
    val text = File(root, "documents-fr/$folder/$name.md").readText()
    return MarkdownPage("example/$folder/$name", "example", MarkdownPage.title(text, name), text)
  }

  fun document(path: String): StudyDocument =
    StudyDocumentReader().read(File(root, "documents-fr/$path/document.json").toPath())

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
