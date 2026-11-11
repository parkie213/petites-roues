package com.petitesroues.android.testing

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.petitesroues.android.desktop.Nsd
import com.petitesroues.sync.PhoneRoutes
import java.net.InetAddress
import java.util.concurrent.Executor

/**
 * Android's service discovery in memory: a search finds each service in [announced] and its lookup
 * answers with that announcement. With nothing announced a search fails to start, so it ends at
 * once instead of waiting out the timeout. Like Android, it can call back after a search is over
 * ([discovery], [confirmUnregistrations]).
 */
class FakeNsd(private val announced: List<NsdServiceInfo> = emptyList()) : Nsd {
  /** How many searches have started. */
  var searches = 0
    private set

  /** The last search's listener, to report a service after it is over. */
  lateinit var discovery: NsdManager.DiscoveryListener

  /** Lookups registered and not yet unregistered, with the executor each was given. */
  val registered = mutableMapOf<NsdManager.ServiceInfoCallback, Executor>()

  private val unregistering = mutableListOf<Pair<NsdManager.ServiceInfoCallback, Executor>>()

  override fun discoverServices(type: String, listener: NsdManager.DiscoveryListener) {
    searches++
    discovery = listener
    if (announced.isEmpty())
      listener.onStartDiscoveryFailed(type, NsdManager.FAILURE_INTERNAL_ERROR)
    else announced.forEach { listener.onServiceFound(service(it.serviceName)) }
  }

  override fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) {}

  override fun registerServiceInfoCallback(
    service: NsdServiceInfo,
    executor: Executor,
    callback: NsdManager.ServiceInfoCallback,
  ) {
    registered[callback] = executor
    announced
      .find { it.serviceName == service.serviceName }
      ?.let { info -> executor.execute { callback.onServiceUpdated(info) } }
  }

  override fun unregisterServiceInfoCallback(callback: NsdManager.ServiceInfoCallback) {
    unregistering += callback to registered.remove(callback)!!
  }

  /** Confirms each unregistration, as Android does some time after it was asked for. */
  fun confirmUnregistrations() {
    unregistering.forEach { (callback, executor) ->
      executor.execute { callback.onServiceInfoCallbackUnregistered() }
    }
  }

  companion object {
    /** A service as discovery reports it, by name only, before its lookup. */
    fun service(name: String) = NsdServiceInfo().apply { serviceName = name }

    /** A desktop's announcement: its phone listener at [address] and [port], with [pin]. */
    fun announcement(name: String, pin: String, address: String, port: Int = 8878) =
      service(name).apply {
        this.port = port
        hostAddresses = listOf(InetAddress.getByName(address))
        setAttribute(PhoneRoutes.SERVICE_PIN, pin)
      }
  }
}
