package com.petitesroues.android.desktop

import android.util.Log
import com.petitesroues.android.Settings
import com.petitesroues.model.sync.Desktop
import com.petitesroues.sync.DesktopException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The desktop this phone is paired with ([Settings.desktop]), reached where it is now: when its
 * saved address stops answering, typically because the router gave the computer a new one,
 * [DesktopFinder] looks for it on the local network and the new address is saved once it works.
 */
@Singleton
class PairedDesktop
@Inject
constructor(private val settings: Settings, private val finder: DesktopFinder) {
  /**
   * Runs [call] against the paired desktop. When its saved address does not answer, finds the
   * desktop, runs [call] there and, if that works, saves the new address. A refusal
   * ([DesktopException], including a certificate that is not the paired one) is never retried, and
   * when the desktop is not found elsewhere the original failure is thrown. Blocks: call it off the
   * main thread.
   *
   * @throws IllegalStateException if the phone is not paired
   */
  fun <T> reach(call: (Desktop) -> T): T {
    val desktop = settings.desktop ?: error("This phone is not paired with a desktop")
    try {
      return call(desktop)
    } catch (unreachable: IOException) {
      if (unreachable is DesktopException) throw unreachable
      val url = finder.find(desktop.pin()) ?: throw unreachable
      if (url == desktop.url()) throw unreachable
      Log.i("Desktop", "${desktop.url()} did not answer; found the desktop at $url")
      val moved = Desktop(desktop.name(), url, desktop.token(), desktop.pin())
      // The client still checks the pin, so only the paired desktop can answer at the new address.
      val result = call(moved)
      settings.desktop = moved
      return result
    }
  }
}
