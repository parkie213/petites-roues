package com.petitesroues.android.di

import com.petitesroues.android.desktop.AndroidNsd
import com.petitesroues.android.desktop.Nsd
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Finding the paired desktop on the local network: Android's service discovery behind [Nsd]. */
@Module
@InstallIn(SingletonComponent::class)
interface DesktopModule {
  @Binds fun nsd(android: AndroidNsd): Nsd
}
