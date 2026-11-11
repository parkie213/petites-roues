package app.petitesroues.android.pages

import app.petitesroues.model.library.MarkdownPageEntry
import app.petitesroues.model.library.Repository
import app.petitesroues.model.pages.MarkdownPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MarkdownPageKeyTest {
  @Test
  fun preservesSelectionKeysAndDistinguishesCollections() {
    val content = MarkdownPage("Verbs", "# Verbs")
    val root = Repository("primary:study", "French")
    val quiz = MarkdownPageEntry(root, "quizzes/verbs.md", content)
    val note = MarkdownPageEntry(root, "notes/verbs.md", content)
    assertEquals("primary:study/quizzes/verbs", quiz.key)
    assertNotEquals(note.key, quiz.key)
  }
}
