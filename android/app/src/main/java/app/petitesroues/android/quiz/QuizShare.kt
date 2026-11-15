package app.petitesroues.android.quiz

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.pages.MarkdownPage
import app.petitesroues.quiz.QuizGuides
import app.petitesroues.quiz.QuizType
import app.petitesroues.quiz.VideoQuizBuilder
import app.petitesroues.quiz.VideoQuizBuilder.Guides
import app.petitesroues.quiz.VideoQuizScope
import java.io.File

/**
 * Hands a quiz brief to another app (the Claude app) through Android's share sheet, as an attached
 * Markdown file plus a short opening message. The file is rewritten in the app's cache on each
 * share; nothing is kept elsewhere.
 */
object QuizShare {
  /** The video's quiz of type [typeId] over [scope], built by the shared [VideoQuizBuilder]. */
  fun share(context: Context, document: StudyDocument, scope: VideoQuizScope, typeId: String) {
    val brief = VideoQuizBuilder.build(QuizGuides.bundled(), document, scope, QuizType.of(typeId))
    send(context, brief.fileName(), document.title(), brief.text())
  }

  /** Shares the brief that [build] makes from the bundled guides and the quiz type [typeId]. */
  fun share(
    context: Context,
    document: StudyDocument,
    fileName: String,
    typeId: String,
    build: (guides: Guides, type: QuizType) -> String,
  ) = send(context, fileName, document.title(), build(QuizGuides.bundled(), QuizType.of(typeId)))

  /**
   * Shares a general quiz from a content folder's `quizzes/`: straight to the app [app] names (a
   * package) when it is installed, otherwise through the share sheet.
   */
  fun share(context: Context, quiz: MarkdownPage, app: String? = null) =
    send(context, QuizPack.fileName(quiz), quiz.title(), prompt(quiz), app)

  /** A general quiz's whole prompt, as shared. */
  fun prompt(quiz: MarkdownPage): String = QuizPack.general(QuizGuides.bundled().rules(), quiz)

  private fun send(
    context: Context,
    fileName: String,
    subject: String,
    pack: String,
    app: String? = null,
  ) {
    val folder = File(context.cacheDir, "quiz").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, fileName).apply { writeText(pack) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    val send =
      Intent(Intent.ACTION_SEND).apply {
        // text/plain rather than text/markdown: more chat apps accept it as an attachment.
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Quiz me on $subject using the attached file.")
        putExtra(Intent.EXTRA_TITLE, file.name)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    if (app != null)
      try {
        return context.startActivity(Intent(send).setPackage(app))
      } catch (_: ActivityNotFoundException) {}
    context.startActivity(Intent.createChooser(send, "Quiz me with…"))
  }
}
