package com.petitesroues.android.study

import com.petitesroues.model.PhraseAlignmentValidator
import com.petitesroues.model.document.PhraseAlignment
import com.petitesroues.model.document.StudyCue

// Phrase-level study help shared by the video player and the comic reader, mirroring the
// browser's `alignment.js`. Chunk indices refer to phrases, not words.

/**
 * The cue's phrase breakdown while it still matches the cue's text; otherwise null, and the line
 * is shown as plain text.
 */
fun readyAlignment(cue: StudyCue): PhraseAlignment? {
  val alignment = cue.alignment()
  if (alignment == null || cue.alignmentStale()) return null
  return try {
    PhraseAlignmentValidator.validateFor(alignment, cue.source(), cue.translation())
    alignment
  } catch (_: IllegalArgumentException) {
    null
  }
}

/** The link that colours a source phrase, or -1. */
fun PhraseAlignment.linkOfSource(sourceIndex: Int): Int =
  links().indexOfFirst { sourceIndex in it.source() }

/** Each distinct explanation attached to a source phrase, in link order. */
fun PhraseAlignment.phraseNotes(sourceIndex: Int): List<String> =
  links().filter { sourceIndex in it.source() }.map { it.note() }.distinct()

/** One translation chunk with the link that colours it and whether it belongs to the explored phrase. */
data class ContextChunk(val text: String, val link: Int, val active: Boolean, val sourcePhrase: Int?)

/** The whole translated line in its own word order, as the browser's translation strip shows it. */
fun PhraseAlignment.translationContext(sourceIndex: Int?): List<ContextChunk> {
  val active =
    if (sourceIndex == null) emptySet()
    else links().filter { sourceIndex in it.source() }.flatMap { it.translation() }.toSet()
  return translation().mapIndexed { index, text ->
    val link = links().indexOfFirst { index in it.translation() }
    ContextChunk(text, link, index in active, links().getOrNull(link)?.source()?.firstOrNull())
  }
}

/** A run of note text; highlighted runs are source-language expressions marked with backticks. */
data class NoteSegment(val text: String, val highlighted: Boolean)

private val sourceSpan = Regex("`[^`\r\n]+`")

/** Splits a learner note like the browser's `noteSegments`; everything else stays literal. */
fun noteSegments(text: String): List<NoteSegment> {
  val segments = mutableListOf<NoteSegment>()
  var last = 0
  for (match in sourceSpan.findAll(text)) {
    if (match.range.first > last) segments += NoteSegment(text.substring(last, match.range.first), false)
    segments += NoteSegment(match.value.substring(1, match.value.length - 1), true)
    last = match.range.last + 1
  }
  if (last < text.length) segments += NoteSegment(text.substring(last), false)
  return segments
}
