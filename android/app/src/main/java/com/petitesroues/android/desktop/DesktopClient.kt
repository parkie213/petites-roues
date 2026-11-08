package com.petitesroues.android.desktop

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * What the desktop's QR code holds (its "Connect a phone" dialog): the desktop's name, the phone
 * listener's base URLs to try in order, and a one-time pairing secret. Version 1 is plain HTTP;
 * a later version adds the certificate fingerprint to pin.
 */
data class PairingCode(val version: Int, val name: String, val urls: List<String>, val secret: String) {
  companion object {
    /** Reads a scanned QR code, or throws [IllegalArgumentException] when it is not a pairing code. */
    fun parse(text: String): PairingCode {
      val json =
        runCatching { mapper.readTree(text) }.getOrNull()?.takeIf { it.isObject }
          ?: throw IllegalArgumentException("This QR code is not from petites roues")
      val version = json.path("v").asInt(0)
      require(version == 1) {
        if (version > 1) "Update the app to pair with this desktop" else "This QR code is not from petites roues"
      }
      val urls = json.path("urls").valueStream().toList().map { it.asString() }.filter { it.startsWith("http://") }
      val secret = json.path("pair").asString("")
      require(urls.isNotEmpty() && secret.isNotEmpty()) { "This pairing code is incomplete" }
      return PairingCode(version, json.path("name").asString("Desktop"), urls, secret)
    }
  }
}

/** A desktop this phone is paired with: where it answered, and the phone's token for it. */
data class Desktop(val name: String, val url: String, val token: String)

/** The desktop's answer to the connection test: its name, version and library size. */
data class Hello(val name: String, val version: String?, val titles: Int, val media: Int)

/** Raised with the desktop's own message when it refuses a request (expired code, forgotten phone). */
class DesktopException(val status: Int, message: String) : IOException(message)

private val mapper = JsonMapper.builder().build()

/**
 * Talks to the desktop's phone listener over HTTP. Blocking: call it off the main thread. Every
 * request but pairing carries the phone's bearer token.
 */
object DesktopClient {
  private const val CONNECT_TIMEOUT_MS = 3_000
  private const val READ_TIMEOUT_MS = 10_000

  /**
   * Pairs with the desktop: tries each of the code's URLs in order and pairs through the first that
   * answers. Throws [DesktopException] when the desktop refuses (the code expired or was used), or
   * the last connection error when none answers.
   */
  fun pair(code: PairingCode, phoneName: String): Desktop {
    var failure: IOException? = null
    for (url in code.urls) {
      try {
        val body = mapper.writeValueAsString(mapOf("secret" to code.secret, "name" to phoneName))
        val answer = request("POST", "$url/phone/pair", null, body)
        return Desktop(answer.path("name").asString(code.name), url, answer.path("token").asString())
      } catch (refused: DesktopException) {
        throw refused
      } catch (unreachable: IOException) {
        failure = unreachable
      }
    }
    throw failure ?: IOException("No address to try")
  }

  /** The connection test. */
  fun hello(desktop: Desktop): Hello {
    val answer = request("GET", "${desktop.url}/phone/hello", desktop.token, null)
    return Hello(
      answer.path("name").asString(desktop.name),
      answer.path("version").takeIf { it.isString }?.asString(),
      answer.path("titles").asInt(),
      answer.path("media").asInt(),
    )
  }

  private fun request(method: String, url: String, token: String?, body: String?): JsonNode {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
      connection.requestMethod = method
      connection.connectTimeout = CONNECT_TIMEOUT_MS
      connection.readTimeout = READ_TIMEOUT_MS
      connection.setRequestProperty("Accept", "application/json")
      if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
      if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
      }
      val status = connection.responseCode
      val stream = if (status in 200..299) connection.inputStream else connection.errorStream
      val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
      if (status !in 200..299) throw DesktopException(status, message(status, text))
      return mapper.readTree(text)
    } finally {
      connection.disconnect()
    }
  }

  /**
   * The desktop's error message, or a plain one for its status. The app's own errors carry it in
   * `error`; Spring's error page in `message`, beside the status name in `error`.
   */
  internal fun message(status: Int, text: String): String {
    val given =
      runCatching { mapper.readTree(text) }.getOrNull()?.let { it.path("message").asString("").ifEmpty { it.path("error").asString("") } }
    return given?.ifBlank { null }
      ?: when (status) {
        401 -> "This phone is no longer paired. Pair it again."
        404 -> "That address is not a petites roues desktop."
        else -> "The desktop answered $status."
      }
  }
}
