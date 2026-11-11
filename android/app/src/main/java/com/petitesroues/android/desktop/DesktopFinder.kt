package com.petitesroues.android.desktop

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.petitesroues.android.Settings
import com.petitesroues.model.sync.Desktop
import com.petitesroues.sync.DesktopException
import com.petitesroues.sync.PhoneRoutes
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Runs [call] against the paired desktop. When its saved address does not answer, typically because
 * the router gave the computer a new one, finds the desktop on the local network ([DesktopFinder]),
 * runs [call] there and, if that works, saves the new address. A refusal ([DesktopException],
 * including a certificate that is not the paired one) is never retried. Blocks: call it off the
 * main thread.
 *
 * @throws IllegalStateException if the phone is not paired
 */
fun <T> reachDesktop(context: Context, settings: Settings, call: (Desktop) -> T): T {
  val desktop = settings.desktop ?: error("This phone is not paired with a desktop")
  try {
    return call(desktop)
  } catch (unreachable: IOException) {
    if (unreachable is DesktopException) throw unreachable
    val url = DesktopFinder.find(context, desktop.pin()) ?: throw unreachable
    if (url == desktop.url()) throw unreachable
    Log.i("Desktop", "${desktop.url()} did not answer; found the desktop at $url")
    val moved = Desktop(desktop.name(), url, desktop.token(), desktop.pin())
    // The client still checks the pin, so only the paired desktop can answer at the new address.
    val result = call(moved)
    settings.desktop = moved
    return result
  }
}

/**
 * Finds the paired desktop's phone listener on the local network: the desktop announces
 * [PhoneRoutes.SERVICE_TYPE] with its certificate's pin while phones may connect. Uses Android's
 * [NsdManager], which needs the same local network permission as connecting.
 */
object DesktopFinder {
  private const val TIMEOUT_SECONDS = 6L

  /**
   * Callbacks run on the thread [NsdManager] delivers them on: they only queue a URL. Android can
   * still deliver one after [find] returns (an unregistration, a late answer), so this must never
   * reject a task, as a shut-down executor would by crashing the app on that thread.
   */
  private val direct = Executor { it.run() }

  /**
   * The parts of [NsdManager] that [find] uses. Android calls the listeners and callbacks on its
   * own thread, possibly after [find] has returned; a test stands in a fake to do the same.
   */
  interface Nsd {
    fun discoverServices(type: String, listener: NsdManager.DiscoveryListener)

    fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener)

    fun registerServiceInfoCallback(
      service: NsdServiceInfo,
      executor: Executor,
      callback: NsdManager.ServiceInfoCallback,
    )

    fun unregisterServiceInfoCallback(callback: NsdManager.ServiceInfoCallback)
  }

  /** The listener's base URL announced with [pin], or null if none answers in a few seconds. */
  fun find(context: Context, pin: String): String? {
    val manager = context.getSystemService(NsdManager::class.java) ?: return null
    return find(
      object : Nsd {
        override fun discoverServices(type: String, listener: NsdManager.DiscoveryListener) =
          manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)

        override fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) =
          manager.stopServiceDiscovery(listener)

        override fun registerServiceInfoCallback(
          service: NsdServiceInfo,
          executor: Executor,
          callback: NsdManager.ServiceInfoCallback,
        ) = manager.registerServiceInfoCallback(service, executor, callback)

        override fun unregisterServiceInfoCallback(callback: NsdManager.ServiceInfoCallback) =
          manager.unregisterServiceInfoCallback(callback)
      },
      pin,
    )
  }

  /** [find] through [nsd]: the URL announced with [pin], or null after [TIMEOUT_SECONDS]. */
  fun find(nsd: Nsd, pin: String): String? {
    // Each found desktop's URL, or "" when discovery cannot start.
    val found = LinkedBlockingQueue<String>()
    val resolving = mutableListOf<NsdManager.ServiceInfoCallback>()
    // Set once the search is over; a desktop found after that is not resolved.
    var finished = false
    val discovery =
      object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
          val callback =
            object : NsdManager.ServiceInfoCallback {
              override fun onServiceUpdated(info: NsdServiceInfo) {
                url(pin, info.attributes, info.hostAddresses, info.port)?.let(found::offer)
              }

              override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}

              override fun onServiceLost() {}

              override fun onServiceInfoCallbackUnregistered() {}
            }
          synchronized(resolving) {
            if (finished) return
            resolving += callback
            nsd.registerServiceInfoCallback(service, direct, callback)
          }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          Log.w("Desktop", "Cannot look for the desktop on the network: error $errorCode")
          found.offer("")
        }

        override fun onDiscoveryStarted(serviceType: String) {}

        override fun onDiscoveryStopped(serviceType: String) {}

        override fun onServiceLost(service: NsdServiceInfo) {}

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
      }
    nsd.discoverServices(PhoneRoutes.SERVICE_TYPE, discovery)
    return try {
      found.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)?.takeIf { it.isNotEmpty() }
    } finally {
      synchronized(resolving) {
        finished = true
        resolving.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
      }
      runCatching { nsd.stopServiceDiscovery(discovery) }
    }
  }

  /**
   * The `https` base URL of an announcement carrying [pin], or null for another desktop's: its
   * first IPv4 address, else a global IPv6 one (a link-local address would need its interface).
   */
  fun url(
    pin: String,
    attributes: Map<String, ByteArray?>,
    addresses: List<InetAddress>,
    port: Int,
  ): String? {
    if (attributes[PhoneRoutes.SERVICE_PIN]?.decodeToString() != pin || port <= 0) return null
    val ipv4 = addresses.firstOrNull { it is Inet4Address }
    if (ipv4 != null) return "https://${ipv4.hostAddress}:$port"
    val ipv6 = addresses.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress } ?: return null
    return "https://[${ipv6.hostAddress}]:$port"
  }
}
