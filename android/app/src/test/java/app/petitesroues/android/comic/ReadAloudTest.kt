package app.petitesroues.android.comic

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudTest {
  private val french = Locale.FRENCH

  @Test
  fun capitalLetteringIsLowercased() {
    assertEquals("c’est un cauchemar !", spoken("C’EST UN CAUCHEMAR !", french))
    assertEquals("nezuko !", spoken("NEZUKO !", french))
  }

  @Test
  fun mixedCaseIsKept() {
    assertEquals("Chapitre 1 - Cruauté", spoken("Chapitre 1 - Cruauté", french))
  }

  @Test
  fun ellipsesBecomePauses() {
    assertEquals("non, je ne sais pas", spoken("NON... JE NE SAIS PAS......", french))
    assertEquals("quoi", spoken("…QUOI", french))
    assertEquals("", spoken("......", french))
  }
}
