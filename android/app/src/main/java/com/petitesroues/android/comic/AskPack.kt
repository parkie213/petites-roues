package com.petitesroues.android.comic

import com.petitesroues.android.study.phraseNotes
import com.petitesroues.android.study.readyAlignment
import com.petitesroues.model.document.ComicCue
import com.petitesroues.model.document.StudyCue
import com.petitesroues.model.document.StudyDocument
import java.util.Locale

/**
 * A short, self-contained message asking a chat app (Claude, ChatGPT) about one speech bubble:
 * the learner's question, the bubble with its translation and notes, the phrase they were
 * exploring, and the page's other bubbles for context. Plain text, so it can start a new chat or
 * be pasted into an existing one.
 */
object AskPack {
  /**
   * The message for [study] on [pageName] of [document]: a bubble's study, or the sentence of a
   * text block being read. [pageCues] are the page's boxes in reading order (including the one
   * holding [study]), whose bubbles and sentences give the context; [phrase] is the source phrase
   * the learner was exploring, if any; a blank [question] asks for a deeper explanation.
   */
  fun build(
    document: StudyDocument,
    pageName: String,
    study: StudyCue,
    pageCues: List<ComicCue>,
    phrase: Int?,
    question: String,
  ): String {
    val alignment = readyAlignment(study)
    val source = language(document.sourceLanguage())
    val notes = language(document.notesLanguage())
    val explored = phrase?.let { alignment?.source()?.getOrNull(it) }
    return buildString {
      appendLine(
        "I'm learning $source by reading “${document.title()}” and have a question about a " +
          "speech bubble on page $pageName."
      )
      appendLine()
      if (question.isNotBlank()) appendLine("My question: ${question.trim()}")
      else appendLine("Please explain this bubble in more depth.")
      if (explored != null) appendLine("I'm asking about the phrase “$explored”.")
      appendLine()
      appendLine("Bubble: ${study.source()}")
      study.translation()?.takeIf { it.isNotBlank() }?.let { appendLine("Translation: $it") }
      val phrases =
        alignment?.let {
          it.source().indices.mapNotNull { index ->
            val links = it.links().filter { link -> index in link.source() }
            if (links.isEmpty()) return@mapNotNull null
            val translation =
              links.flatMap { link -> link.translation() }.distinct().sorted().joinToString(" … ") { chunk -> it.translation()[chunk] }
            val notesText = it.phraseNotes(index).filter { note -> note.isNotBlank() }.joinToString(" ")
            "- “${it.source()[index]}” → $translation" + if (notesText.isEmpty()) "" else ": $notesText"
          }
        }.orEmpty()
      if (phrases.isNotEmpty()) {
        appendLine()
        appendLine("Phrase notes from my reader:")
        phrases.forEach { appendLine(it) }
      }
      if (study.notes().isNotEmpty()) {
        appendLine()
        appendLine("Sentence notes:")
        study.notes().forEach { appendLine("- $it") }
      }
      val others = pageCues.flatMap { it.studies() }.filter { it.source().isNotBlank() }
      if (others.size > 1) {
        appendLine()
        appendLine("The page's bubbles in reading order, for context (→ marks this one):")
        for (other in others) {
          val line = other.line()
          appendLine(if (other.id() == study.id()) "→ $line" else "- $line")
        }
      }
      appendLine()
      append(
        "Please answer in $notes. Explain why the $source means what it does here — idioms, " +
          "pronominal verbs and grammar — not just a translation. Keep it concise."
      )
    }
  }

  private fun StudyCue.line(): String =
    source() + (translation()?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")

  /** "fr" → "French"; falls back to the tag itself. */
  private fun language(tag: String): String =
    Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH).ifBlank { tag }
}
