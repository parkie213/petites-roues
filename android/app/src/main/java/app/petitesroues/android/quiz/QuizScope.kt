package app.petitesroues.android.quiz

import app.petitesroues.model.document.StudyDocument
import app.petitesroues.model.document.VideoCue

/**
 * Which of a video's lines a quiz brief carries, the phone's counterpart of the browser's "Whole
 * video" and "Time range" choice (`quiz.js`), plus the phone-only [Last] lines. The rules for
 * choosing a default are written once in `agents/workflows/subtitle-quiz.md`.
 */
sealed interface QuizScope {
  /** Every line of the title. */
  data object Whole : QuizScope

  /**
   * Lines overlapping the time range from [start] to [end] seconds, as the browser selects them.
   */
  data class Range(val start: Double, val end: Double) : QuizScope

  /** Up to [count] lines ending at index [current] of the sorted cues. */
  data class Last(val current: Int, val count: Int = LAST_LINES) : QuizScope

  /** The scope choices the dialog offers. */
  enum class Choice {
    WHOLE,
    RANGE,
    LAST,
  }

  /** What the dialog opens on: a [choice] and, for a range, its start and end as typed text. */
  data class Initial(val choice: Choice, val start: String, val end: String)

  companion object {
    /** How many lines [Last] carries unless told otherwise. */
    const val LAST_LINES = 30

    /** Above this many lines the dialog warns and suggests a smaller scope; it never truncates. */
    const val SUGGESTED_CAP = 400

    /** Titles shorter than this many seconds start on [Whole]. */
    const val SHORT_SECONDS = 15 * 60.0

    /** The length of the time range a longer title starts on, ending at the position. */
    const val RECENT_SECONDS = 10 * 60.0

    private val TIME = Regex("^\\d+:\\d{2}(?::\\d{2})?$")

    /**
     * Parses M:SS or H:MM:SS into seconds; anything else, or minutes or seconds over 59, is null.
     * The same rules as the browser's `parseQuizTime`.
     */
    fun parseTime(value: String): Double? {
      if (!TIME.matches(value)) return null
      val parts = value.split(":").map { it.toLong() }
      if (parts.last() > 59 || (parts.size == 3 && parts[1] > 59)) return null
      return parts.fold(0L) { total, part -> total * 60 + part }.toDouble()
    }

    /** "m:ss", or "h:mm:ss" from an hour, the text [parseTime] reads back. */
    fun clock(seconds: Double): String {
      val total = seconds.toInt().coerceAtLeast(0)
      return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
      else "%d:%02d".format(total / 60, total % 60)
    }

    /**
     * The title's length in seconds: the document's recorded duration, else [playerSeconds] (the
     * player's own, when known), else the end of the last cue.
     */
    fun length(
      document: StudyDocument,
      cues: List<VideoCue>,
      playerSeconds: Double? = null,
    ): Double =
      document.video().reference()?.durationSeconds?.takeIf { it > 0 }
        ?: playerSeconds?.takeIf { it > 0 && it.isFinite() }
        ?: cues.maxOfOrNull { it.end() }
        ?: 0.0

    /**
     * The lines that [scope] holds in [cues] (sorted by start): those with source text, since lines
     * without it cannot be quizzed.
     */
    fun select(cues: List<VideoCue>, scope: QuizScope): List<VideoCue> {
      val quizzable = { cue: VideoCue -> cue.line().source().isNotBlank() }
      return when (scope) {
        Whole -> cues.filter(quizzable)
        is Range ->
          cues.filter { quizzable(it) && it.start() < scope.end && it.end() > scope.start }
        is Last ->
          cues
            .subList(0, (scope.current + 1).coerceIn(0, cues.size))
            .filter(quizzable)
            .takeLast(scope.count)
      }
    }

    /** Whether [count] lines is more than [SUGGESTED_CAP]. */
    fun overCap(count: Int): Boolean = count > SUGGESTED_CAP

    /**
     * Where the dialog opens: [Choice.WHOLE] when the title is shorter than [SHORT_SECONDS] or its
     * document names a shelf other than Films (music-style videos); otherwise [Choice.RANGE]
     * prefilled with the last [RECENT_SECONDS] watched, ending at [position]. Nothing is remembered
     * from other titles.
     */
    fun initial(document: StudyDocument, length: Double, position: Double): Initial {
      val shelf = document.shelf()
      val whole = length < SHORT_SECONDS || (shelf != null && !shelf.equals("Films", true))
      val end = if (position < 1) minOf(RECENT_SECONDS, length) else position
      val start = (end - RECENT_SECONDS).coerceAtLeast(0.0)
      return Initial(if (whole) Choice.WHOLE else Choice.RANGE, clock(start), clock(end))
    }

    /**
     * The scope the dialog's fields describe, or null while a range is invalid (unparsable, or
     * ending no later than it starts).
     */
    fun of(choice: Choice, start: String, end: String, current: Int): QuizScope? =
      when (choice) {
        Choice.WHOLE -> Whole
        Choice.LAST -> Last(current)
        Choice.RANGE -> {
          val from = parseTime(start.trim())
          val to = parseTime(end.trim())
          if (from == null || to == null || to <= from) null else Range(from, to)
        }
      }
  }
}
