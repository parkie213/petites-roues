package app.petitesroues.android.testing

import app.petitesroues.sync.DesktopClient
import app.petitesroues.sync.DesktopException
import app.petitesroues.sync.model.Desktop
import app.petitesroues.sync.model.DesktopTitle
import app.petitesroues.sync.model.Hello
import app.petitesroues.sync.model.PairingCode
import app.petitesroues.sync.model.TitleFile

/**
 * A paired desktop answering in memory, in place of the network: it pairs with any well-formed
 * code, offers [titles], and refuses everything while [refusing] is set. A refusal, unlike a lost
 * connection, isn't followed by a search of the local network.
 */
class FakeDesktop(var titles: List<DesktopTitle> = emptyList()) : DesktopClient() {
  var refusing: String? = null

  override fun pair(code: PairingCode, phoneName: String): Desktop {
    refuse()
    return Desktop(code.name(), code.urls().first(), "token", code.cert())
  }

  override fun hello(desktop: Desktop): Hello {
    refuse()
    return Hello(desktop.name(), "test", titles.size, titles.size)
  }

  override fun titles(desktop: Desktop): List<DesktopTitle> {
    refuse()
    return titles
  }

  private fun refuse() = refusing?.let { throw DesktopException(403, it) }

  companion object {
    /** A valid certificate pin. */
    val PIN = "sha256:" + "ab".repeat(32)

    /** The desktop that [PAIRING_CODE] pairs with. */
    val DESKTOP = Desktop("Bureau", "https://192.168.1.5:8878", "token", PIN)

    /** What the desktop's QR code holds. */
    val PAIRING_CODE =
      """{"v":2,"name":"Bureau","urls":["https://192.168.1.5:8878"],"pair":"s3cret","cert":"$PIN"}"""

    /** Volume [volume] of the comic series [series], a single CBZ of [bytes]. */
    fun comic(series: String, volume: Int, bytes: Long = 1_000) =
      DesktopTitle(
        "$series-$volume",
        "$series $volume",
        series,
        "comic",
        null,
        null,
        volume,
        null,
        listOf(TitleFile(TitleFile.MEDIA, "comics/$series/$volume.cbz", bytes, "f$volume", null)),
      )
  }
}
