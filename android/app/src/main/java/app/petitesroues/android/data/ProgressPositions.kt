package app.petitesroues.android.data

import app.petitesroues.model.progress.PlaybackPosition
import app.petitesroues.model.progress.ReadingPosition

/** Restores shared position values from a local Room snapshot without carrying its storage key. */
fun Progress.playbackPosition(): PlaybackPosition? = positionSeconds?.let(::PlaybackPosition)

/** A snapshot without a comic page has no reading position. */
fun Progress.readingPosition(): ReadingPosition? = pageId?.let(::ReadingPosition)
