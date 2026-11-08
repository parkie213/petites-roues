package com.petitesroues.android.desktop

import com.petitesroues.sync.PhoneRoutes
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopFinderTest {
  private val pin = "sha256:" + "ab".repeat(32)
  private val announced = mapOf<String, ByteArray?>(PhoneRoutes.SERVICE_PIN to pin.encodeToByteArray())
  private fun address(text: String) = InetAddress.getByName(text)

  @Test
  fun thePairedDesktopsAnnouncementGivesItsIpv4Url() {
    val addresses = listOf(address("2001:db8::7"), address("192.168.1.42"))
    assertEquals("https://192.168.1.42:8878", DesktopFinder.url(pin, announced, addresses, 8878))
  }

  @Test
  fun aGlobalIpv6AddressIsBracketed() {
    val global = address("2001:db8::7")
    val addresses = listOf(address("fe80::1"), global)
    assertEquals("https://[${global.hostAddress}]:8878", DesktopFinder.url(pin, announced, addresses, 8878))
  }

  @Test
  fun anotherDesktopsOrAPinlessAnnouncementIsIgnored() {
    val addresses = listOf(address("192.168.1.42"))
    val other = mapOf<String, ByteArray?>(PhoneRoutes.SERVICE_PIN to ("sha256:" + "cd".repeat(32)).encodeToByteArray())
    assertNull(DesktopFinder.url(pin, other, addresses, 8878))
    assertNull(DesktopFinder.url(pin, emptyMap(), addresses, 8878))
  }

  @Test
  fun onlyALinkLocalAddressCannotBeUsed() {
    assertNull(DesktopFinder.url(pin, announced, listOf(address("fe80::1")), 8878))
  }
}
