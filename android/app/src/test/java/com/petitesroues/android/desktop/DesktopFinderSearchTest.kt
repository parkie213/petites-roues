package com.petitesroues.android.desktop

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.petitesroues.android.screens.SDK
import com.petitesroues.sync.PhoneRoutes
import java.net.InetAddress
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DesktopFinder.find] against a fake [DesktopFinder.Nsd] that, like Android, can call back after
 * the search is over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK])
class DesktopFinderSearchTest {
  private val pin = "sha256:" + "ab".repeat(32)

  private fun service(name: String) = NsdServiceInfo().apply { serviceName = name }

  private val desktop =
    service("desktop").apply {
      port = 8878
      hostAddresses = listOf(InetAddress.getByName("192.168.1.42"))
      setAttribute(PhoneRoutes.SERVICE_PIN, pin)
    }

  /** Reports [found] when discovery starts and answers lookups for services in [answers]. */
  private class FakeNsd(
    private val found: List<NsdServiceInfo>,
    private val answers: Map<String, NsdServiceInfo>,
  ) : DesktopFinder.Nsd {
    lateinit var discovery: NsdManager.DiscoveryListener
    /** Lookups registered and not yet unregistered, with the executor each was given. */
    val registered = mutableMapOf<NsdManager.ServiceInfoCallback, Executor>()
    private val unregistering = mutableListOf<Pair<NsdManager.ServiceInfoCallback, Executor>>()

    override fun discoverServices(type: String, listener: NsdManager.DiscoveryListener) {
      discovery = listener
      found.forEach(listener::onServiceFound)
    }

    override fun stopServiceDiscovery(listener: NsdManager.DiscoveryListener) {}

    override fun registerServiceInfoCallback(
      service: NsdServiceInfo,
      executor: Executor,
      callback: NsdManager.ServiceInfoCallback,
    ) {
      registered[callback] = executor
      answers[service.serviceName]?.let { info ->
        executor.execute { callback.onServiceUpdated(info) }
      }
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
  }

  @Test
  fun aLateConfirmationAfterTheSearchIsHarmless() {
    val nsd = FakeNsd(listOf(service("desktop")), mapOf("desktop" to desktop))
    assertEquals("https://192.168.1.42:8878", DesktopFinder.find(nsd, pin))
    // Android confirms the unregistration after find has returned; this must not throw.
    nsd.confirmUnregistrations()
  }

  @Test
  fun aDesktopFoundAfterTheSearchIsNotLookedUp() {
    val nsd = FakeNsd(listOf(service("desktop")), mapOf("desktop" to desktop))
    assertEquals("https://192.168.1.42:8878", DesktopFinder.find(nsd, pin))
    nsd.discovery.onServiceFound(service("late"))
    assertTrue("lookups left registered: ${nsd.registered.size}", nsd.registered.isEmpty())
  }
}
