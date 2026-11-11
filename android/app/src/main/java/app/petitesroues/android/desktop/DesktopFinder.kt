package app.petitesroues.android.desktop

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import app.petitesroues.sync.PhoneRoutes
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the paired desktop's phone listener on the local network: the desktop announces
 * [PhoneRoutes.SERVICE_TYPE] with its certificate's pin while phones may connect. Uses Android's
 * service discovery ([Nsd]), which needs the same local network permission as connecting. Each
 * search starts and stops its own discovery.
 */
@Singleton
class DesktopFinder @Inject constructor(private val nsd: Nsd) {
  /**
   * Callbacks run on the thread [NsdManager] delivers them on: they only queue a URL. Android can
   * still deliver one after [find] returns (an unregistration, a late answer), so this must never
   * reject a task, as a shut-down executor would by crashing the app on that thread.
   */
  private val direct = Executor { it.run() }

  /**
   * The listener's base URL announced with [pin], or null if none answers within [TIMEOUT_SECONDS].
   * Blocks while it looks.
   */
  fun find(pin: String): String? {
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

  companion object {
    private const val TIMEOUT_SECONDS = 6L

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
      val ipv6 =
        addresses.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress } ?: return null
      return "https://[${ipv6.hostAddress}]:$port"
    }
  }
}
