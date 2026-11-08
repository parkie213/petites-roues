package com.petitesroues.android.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** The client against a stand-in phone listener on this machine. */
class DesktopClientTest {
  private lateinit var server: HttpServer
  private lateinit var url: String
  private var authorization: String? = null

  @Before
  fun start() {
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/phone/pair") { exchange ->
      val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
      val (status, answer) =
        if ("\"secret\":\"good\"" in body) 200 to """{"token":"t0k","name":"desk"}"""
        else 400 to """{"error":"This pairing code has expired"}"""
      exchange.sendResponseHeaders(status, answer.length.toLong())
      exchange.responseBody.use { it.write(answer.toByteArray()) }
    }
    server.createContext("/phone/hello") { exchange ->
      authorization = exchange.requestHeaders.getFirst("Authorization")
      val answer = """{"name":"desk","version":"0.1.0","titles":3,"media":4}"""
      exchange.sendResponseHeaders(200, answer.length.toLong())
      exchange.responseBody.use { it.write(answer.toByteArray()) }
    }
    server.start()
    url = "http://127.0.0.1:${server.address.port}"
  }

  @After fun stop() = server.stop(0)

  /** A local port nothing listens on: connecting is refused at once. */
  private fun deadUrl(): String = ServerSocket(0).use { "http://127.0.0.1:${it.localPort}" }

  @Test
  fun pairsThroughTheFirstAddressThatAnswersThenSendsItsToken() {
    val desktop = DesktopClient.pair(PairingCode(1, "desk", listOf(deadUrl(), url), "good"), "Pixel")
    assertEquals(Desktop("desk", url, "t0k"), desktop)
    assertEquals(Hello("desk", "0.1.0", 3, 4), DesktopClient.hello(desktop))
    assertEquals("Bearer t0k", authorization)
  }

  @Test
  fun aRefusalStopsAtOnceWithTheDesktopsMessage() {
    val refused =
      assertThrows(DesktopException::class.java) {
        DesktopClient.pair(PairingCode(1, "desk", listOf(url, deadUrl()), "old"), "Pixel")
      }
    assertEquals(400, refused.status)
    assertEquals("This pairing code has expired", refused.message)
  }
}
