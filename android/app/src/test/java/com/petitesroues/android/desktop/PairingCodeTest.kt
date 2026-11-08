package com.petitesroues.android.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingCodeTest {
  @Test
  fun readsTheDesktopsCode() {
    val code =
      PairingCode.parse(
        """{"v":1,"name":"desk","urls":["http://192.168.1.5:8878","http://10.0.0.2:8878"],"pair":"s3cret"}"""
      )
    assertEquals(PairingCode(1, "desk", listOf("http://192.168.1.5:8878", "http://10.0.0.2:8878"), "s3cret"), code)
  }

  @Test
  fun refusesOtherCodesAndNewerVersions() {
    assertThrows(IllegalArgumentException::class.java) { PairingCode.parse("https://example.com") }
    assertThrows(IllegalArgumentException::class.java) { PairingCode.parse("""{"v":1,"urls":[],"pair":"x"}""") }
    val newer =
      assertThrows(IllegalArgumentException::class.java) {
        PairingCode.parse("""{"v":2,"name":"d","urls":["https://a"],"pair":"x","cert":"sha256:00"}""")
      }
    assertEquals("Update the app to pair with this desktop", newer.message)
  }

  @Test
  fun errorMessagesComeFromTheDesktop() {
    assertEquals("Code expired", DesktopClient.message(400, """{"error":"Code expired"}"""))
    assertEquals(
      "Pair this phone with the desktop again",
      DesktopClient.message(401, """{"status":401,"error":"Unauthorized","message":"Pair this phone with the desktop again"}"""),
    )
    assertEquals("This phone is no longer paired. Pair it again.", DesktopClient.message(401, ""))
  }
}
