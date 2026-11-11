package app.petitesroues.android.di

import app.petitesroues.android.desktop.AndroidNsd
import app.petitesroues.android.desktop.Nsd
import app.petitesroues.android.library.AndroidAppFolder
import app.petitesroues.android.library.AppFolder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Platform services behind interfaces, so tests can stand in their own: the app's own folder in
 * shared storage, and service discovery for finding the paired desktop on the local network.
 */
@Module
@InstallIn(SingletonComponent::class)
interface PlatformModule {
  @Binds fun appFolder(android: AndroidAppFolder): AppFolder

  @Binds fun nsd(android: AndroidNsd): Nsd
}
