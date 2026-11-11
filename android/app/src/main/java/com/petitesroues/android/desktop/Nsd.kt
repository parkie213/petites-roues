package com.petitesroues.android.desktop

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * The parts of Android's network service discovery ([NsdManager]) that [DesktopFinder] uses.
 * Android calls the listeners and callbacks on its own thread, possibly after a search is over; a
 * test stands in a fake to do the same.
 */
interface Nsd {
  /** Looks for DNS-SD services of [type], reporting each to [listener] until stopped. */
  fun discoverServices(type: String, listener: NsdManager.DiscoveryListener)

  fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener)

  /** Asks for [service]'s addresses, port and attributes, delivered to [callback] on [executor]. */
  fun registerServiceInfoCallback(
    service: NsdServiceInfo,
    executor: Executor,
    callback: NsdManager.ServiceInfoCallback,
  )

  fun unregisterServiceInfoCallback(callback: NsdManager.ServiceInfoCallback)
}

/**
 * [Nsd] through the app's [NsdManager], looked up once. Without one, every search fails to start,
 * so the desktop is simply not found.
 */
class AndroidNsd @Inject constructor(@ApplicationContext context: Context) : Nsd {
  private val manager: NsdManager? = context.getSystemService(NsdManager::class.java)

  override fun discoverServices(type: String, listener: NsdManager.DiscoveryListener) {
    if (manager == null) listener.onStartDiscoveryFailed(type, NsdManager.FAILURE_INTERNAL_ERROR)
    else manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
  }

  override fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) {
    manager?.stopServiceDiscovery(listener)
  }

  override fun registerServiceInfoCallback(
    service: NsdServiceInfo,
    executor: Executor,
    callback: NsdManager.ServiceInfoCallback,
  ) {
    manager?.registerServiceInfoCallback(service, executor, callback)
  }

  override fun unregisterServiceInfoCallback(callback: NsdManager.ServiceInfoCallback) {
    manager?.unregisterServiceInfoCallback(callback)
  }
}
