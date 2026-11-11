package app.petitesroues.android.pages

/**
 * A Markdown page from a folder at the root of a content folder: a general quiz in `quizzes/` (a
 * prompt for a chat app) or a study note in `notes/` (read in the library). [key] identifies it
 * across content folders, [repository] is its content folder's name, [title] its first `# ` heading
 * (or file name) and [text] the whole file. The app never writes it.
 */
data class MarkdownPage(
  val key: String,
  val repository: String,
  val title: String,
  val text: String,
) {
  companion object {
    /** The folder holding general quizzes. */
    const val QUIZZES = "quizzes"

    /** The folder holding study notes. */
    const val NOTES = "notes"

    /** The first level-one heading's text in [markdown], or [fallback] when it has none. */
    fun title(markdown: String, fallback: String): String =
      markdown
        .lines()
        .filter { it.startsWith("# ") }
        .map { it.substring(2).trim() }
        .firstOrNull { it.isNotEmpty() } ?: fallback
  }
}
