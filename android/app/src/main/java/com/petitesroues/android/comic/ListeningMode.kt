package com.petitesroues.android.comic

/**
 * Per-device bubble tap behaviour; cycling follows off, listen first, then listen with the card.
 */
enum class ListeningMode(val label: String) {
  OFF("Read aloud off"),
  LISTEN_FIRST("First tap reads aloud; second tap opens notes"),
  LISTEN_AND_OPEN("Tap reads aloud and opens notes");

  fun next(): ListeningMode = entries[(ordinal + 1) % entries.size]

  /** Whether this tap starts speech; a second listen-first tap only opens the card. */
  fun readsAloud(alreadyHeard: Boolean): Boolean =
    this == LISTEN_AND_OPEN || (this == LISTEN_FIRST && !alreadyHeard)

  /** Whether this tap opens the study card, independently of speech starting or finishing. */
  fun opensCard(alreadyHeard: Boolean): Boolean = this != LISTEN_FIRST || alreadyHeard
}
