package com.petitesroues.android

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

/** The application: Hilt builds the app-wide objects ([com.petitesroues.android.di]) from here. */
@HiltAndroidApp
class PetitesRouesApp : Application() {
  override fun onCreate() {
    super.onCreate()
    if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) strictMode()
  }

  /**
   * Debug builds log disk, database and network access on the main thread, and leaked resources, as
   * `StrictMode` warnings in logcat (`adb logcat -s StrictMode`). They only log: nothing crashes,
   * so check the log after changing how screens load or save.
   */
  private fun strictMode() {
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
    StrictMode.setVmPolicy(
      StrictMode.VmPolicy.Builder()
        .detectLeakedClosableObjects()
        .detectLeakedSqlLiteObjects()
        .detectActivityLeaks()
        .penaltyLog()
        .build()
    )
  }
}
