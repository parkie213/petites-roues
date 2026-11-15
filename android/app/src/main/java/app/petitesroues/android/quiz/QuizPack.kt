package app.petitesroues.android.quiz

import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.pages.MarkdownPage
import app.petitesroues.quiz.ChapterQuizBuilder
import app.petitesroues.quiz.QuizType
import app.petitesroues.quiz.VideoQuizBuilder.Guides

/**
 * The Markdown briefs for a chat app that the shared video builder does not make: a comic chapter
 * (delegated to [ChapterQuizBuilder]) and a general quiz from a content folder. The video brief is
 * [app.petitesroues.quiz.VideoQuizBuilder]'s.
 */
object QuizPack {
  /**
   * The brief for chapter [index] of [document], with [type]'s instructions (read from the quiz
   * types in [guides]) above the quiz rules.
   */
  fun chapter(guides: Guides, document: StudyDocument, index: Int, type: QuizType? = null): String =
    ChapterQuizBuilder.build(
        guides.rules(),
        document,
        index,
        type?.chosen(guides.types(), document),
      )
      .text()

  /** "quiz-common-irregular-verbs.md" */
  fun fileName(quiz: MarkdownPage): String {
    val slug =
      quiz.title().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "title" }
    return "quiz-$slug.md"
  }

  /**
   * The brief for a general [quiz], as the browser's: what to do, the quiz file itself, then the
   * subtitle quiz [rules] for the teaching approach. The chat model writes its own sentences.
   */
  fun general(rules: String, quiz: MarkdownPage): String =
    listOf(
        "# Quiz: ${quiz.title()}",
        "",
        "Run the quiz below for a learner. It is not tied to a film or book: make up natural, " +
          "varied sentences of your own, as a native speaker would say them, and do not present " +
          "them as quotations. The quiz says which language is studied and which language to " +
          "explain in; where the rules mention the source, translation or notes language, use " +
          "those. Start the first exercise straight away.",
        "",
        "Vary the items: mix verbs, persons and situations rather than working through a list " +
          "in order, and return more often to what the learner finds hard.",
        "",
        "## The quiz",
        "",
        demote(quiz.text()),
        "",
        "## Quiz rules",
        "",
        "These rules were written for quizzes on a title's subtitles. Keep their teaching " +
          "approach (one item at a time, wait for the attempt, diagnose, hint, retry, reveal only " +
          "when asked) and ignore what they say about films, documents, the browser player or " +
          "files from the library.",
        "",
        demote(rules),
        "",
      )
      .joinToString("\n")

  /** Nests a bundled file's headings two levels below this brief's sections. */
  private fun demote(markdown: String): String =
    markdown.trim().lines().joinToString("\n") { if (it.startsWith("#")) "##$it" else it }
}
