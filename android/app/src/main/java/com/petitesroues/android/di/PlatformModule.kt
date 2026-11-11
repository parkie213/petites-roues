package com.petitesroues.android.di

import com.petitesroues.android.desktop.AndroidNsd
import com.petitesroues.android.desktop.Nsd
import com.petitesroues.android.library.AndroidAppFolder
import com.petitesroues.android.library.AppFolder
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
