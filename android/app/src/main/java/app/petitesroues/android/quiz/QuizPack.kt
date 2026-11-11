package app.petitesroues.android.quiz

import app.petitesroues.android.comic.Chapters
import app.petitesroues.android.pages.MarkdownPage
import app.petitesroues.model.document.ComicDocument
import app.petitesroues.model.document.Line
import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.document.VideoCue
import java.util.Locale

/**
 * Self-contained Markdown briefs for running the subtitle quiz in a chat app: the quiz rules
 * (bundled from `agents/` at build time) plus the title's real lines. Each line is its source text
 * and the app's translation, the only parts a translation quiz needs: the chat model writes its own
 * explanations, so learner notes stay out and the brief stays small. Matches the browser's
 * `quiz.js`.
 */
object QuizPack {
  /** Keeps a quiz from working through the lines in order, as chat models otherwise do. */
  private const val SPREAD =
    "Pick items from all over these lines, in a mixed order: do not start at the beginning, " +
      "and do not take two items in a row from the same page or moment."

  /** How many watched lines the video brief carries, ending at the current one. */
  const val LINES = 30

  /**
   * A quiz type offered by Quiz me. Its instructions are the section of the bundled `quiz-types.md`
   * under [heading]; [label] names it for the learner. Matches the browser's list.
   */
  class Type(val id: String, val heading: String, val label: (StudyDocument) -> String)

  val TYPES =
    listOf(
      Type("to-source", "Translate into the source language") {
        "Translate into ${language(it.sourceLanguage())}"
      },
      Type("to-translation", "Translate into the translation language") {
        "Translate into ${language(it.translationLanguage())}"
      },
      Type("comprehension", "Comprehension") { "Comprehension" },
      Type("vocabulary", "Vocabulary") { "Vocabulary" },
      Type("tenses", "Verb tenses") { "Verb tenses" },
      Type("mixed", "Mixed") { "Mixed" },
    )

  /** A chosen quiz type for one brief: its [label] and [instructions]. */
  class Chosen(val id: String, val label: String, val instructions: String)

  /**
   * Quiz type [id] for [document] from the quiz types Markdown [types]; unknown ids give the first.
   */
  fun type(types: String, id: String, document: StudyDocument): Chosen {
    val type = TYPES.firstOrNull { it.id == id } ?: TYPES.first()
    val section =
      types.split(Regex("^## ", RegexOption.MULTILINE)).drop(1).firstOrNull {
        it.startsWith(type.heading + "\n")
      }
    return Chosen(
      type.id,
      type.label(document),
      section?.removePrefix(type.heading)?.trim().orEmpty(),
    )
  }

  private fun language(tag: String): String =
    Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH).ifBlank { tag }

  /** One line of a brief: an optional time [label], an optional page [group], and its cue. */
  class Item(val line: Line, val label: String? = null, val group: String? = null)

  /**
   * The video brief for [document], with up to [LINES] cues ending at index [current] in [cues]
   * (the document's sorted video cues). Lines without source text are skipped.
   */
  fun build(
    rules: String,
    document: StudyDocument,
    cues: List<VideoCue>,
    current: Int,
    type: Chosen? = null,
  ): String {
    val watched =
      cues
        .subList(0, (current + 1).coerceIn(0, cues.size))
        .filter { it.line().source().isNotBlank() }
        .takeLast(LINES)
    return brief(
      rules,
      type,
      document,
      heading = "Subtitle quiz: ${document.title()}",
      intro =
        "Run the subtitle quiz below for a learner on their phone, using the lines they have just " +
          "watched. Start the first exercise straight away. Instructions about the browser player " +
          "do not apply here, and the rules mention files from the library (a teaching guide, " +
          "documents) that are not included: work from the lines below.",
      section =
        if (watched.isEmpty()) "Lines just watched"
        else
          "Lines just watched (${clock(watched.first().start())}–${clock(watched.last().end())})",
      empty = "None yet: the learner is at the start of the film.",
      items = watched.map { Item(it.line(), label = clock(it.start())) },
    )
  }

  /**
   * The chapter brief: every bubble and text block sentence of chapter [index] of [comic], in
   * reading order.
   */
  fun chapter(
    rules: String,
    document: StudyDocument,
    comic: ComicDocument,
    index: Int,
    type: Chosen? = null,
  ): String {
    val chapter = comic.chapters()[index]
    val pages = comic.pages()
    val start = { i: Int -> pages.indexOfFirst { it.id() == comic.chapters()[i].pageId() } }
    val first = start(index)
    val last = if (index + 1 < comic.chapters().size) start(index + 1) - 1 else pages.size - 1
    val items =
      (first..last).flatMap { position ->
        val page = pages[position]
        val name = "Page ${page.label().orEmpty().ifBlank { "${position + 1}" }}"
        page
          .boxes()
          .flatMap { it.lines() }
          .filter { it.source().isNotBlank() }
          .map { Item(it, group = name) }
      }
    val name = Chapters.name(chapter)
    return brief(
      rules,
      type,
      document,
      heading = "Chapter quiz: ${document.title()}, chapter $name",
      intro =
        "Run the subtitle quiz below for a learner on their phone, using the speech bubbles of the " +
          "comic chapter they are reading: in the rules, a subtitle or line means a bubble, and the " +
          "film means this comic. Bubbles are listed in reading order. Start the first exercise " +
          "straight away. Instructions about the browser player do not apply here, and the rules " +
          "mention files from the library (a teaching guide, documents) that are not included: " +
          "work from the lines below.",
      section = "Bubbles in chapter $name (pages ${first + 1}–${last + 1})",
      empty = "None: this chapter's text has not been found yet.",
      items = items,
    )
  }

  /** The Markdown shared by every brief; a page [Item.group] becomes a heading when it changes. */
  private fun brief(
    rules: String,
    type: Chosen?,
    document: StudyDocument,
    heading: String,
    intro: String,
    section: String,
    empty: String,
    items: List<Item>,
  ): String = buildString {
    appendLine("# $heading")
    appendLine()
    appendLine(intro)
    appendLine()
    appendLine(
      "Source language: `${document.sourceLanguage()}`; translation language: " +
        "`${document.translationLanguage()}`; notes language: `${document.notesLanguage()}`. " +
        "Each line below is the source text, then → the app's translation."
    )
    appendLine()
    type?.let {
      appendLine("## Quiz type: ${it.label}")
      appendLine()
      appendLine(it.instructions)
      appendLine()
      appendLine(
        "Whatever the type, keep the approach of the rules below: one item at a time, wait for " +
          "the attempt, diagnose, hint, retry, and reveal only when asked."
      )
      appendLine()
    }
    appendLine("## Quiz rules")
    appendLine()
    appendLine(demote(rules))
    appendLine()
    appendLine("## $section")
    appendLine()
    if (items.isEmpty()) appendLine(empty)
    else {
      appendLine(SPREAD)
      appendLine()
    }
    var group: String? = null
    for (item in items) {
      if (item.group != null && item.group != group) {
        appendLine()
        appendLine("### ${item.group}")
        appendLine()
      }
      group = item.group
      val translation = item.line.translation()?.trim().orEmpty()
      append("- ")
      item.label?.let { append("$it ") }
      append(item.line.source().trim())
      if (translation.isNotEmpty()) append(" → $translation")
      appendLine()
    }
  }

  /** A file name for a brief, from the title and an optional [part] such as "chapter 2". */
  fun fileName(document: StudyDocument, part: String = ""): String =
    fileName("${document.title()} $part")

  private fun fileName(name: String): String {
    val slug =
      name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "title" }
    return "quiz-$slug.md"
  }

  /** "quiz-common-irregular-verbs.md" */
  fun fileName(quiz: MarkdownPage): String = fileName(quiz.title)

  /**
   * The brief for a general [quiz], as the browser's: what to do, the quiz file itself, then the
   * subtitle quiz [rules] for the teaching approach. The chat model writes its own sentences.
   */
  fun general(rules: String, quiz: MarkdownPage): String =
    listOf(
        "# Quiz: ${quiz.title}",
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
        demote(quiz.text),
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

  private fun clock(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
  }
}
