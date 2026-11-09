package com.petitesroues.android

import com.petitesroues.model.document.Line
import com.petitesroues.model.document.PhraseAlignment
import com.petitesroues.model.document.PhraseLink
import com.petitesroues.model.document.VideoCue

fun cue(
  id: String,
  source: String = "",
  translation: String = "",
  alignment: PhraseAlignment? = null,
) = Line(id, source, translation, listOf(), alignment, false, null)

fun line(id: String, start: Double, end: Double) = VideoCue(cue(id), start, end, null)

/** "Il fait beau" → "The weather is nice", with a shared and a crossing link. */
fun weather(): PhraseAlignment =
  PhraseAlignment(
    listOf("Il fait", "beau"),
    listOf("The weather is", "nice"),
    listOf(
      PhraseLink(listOf(0), listOf(0), "`il fait` + adjective describes the weather"),
      PhraseLink(listOf(1), listOf(1), "`beau` here means fine weather"),
    ),
  )
