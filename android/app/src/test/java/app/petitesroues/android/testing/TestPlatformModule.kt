package app.petitesroues.android.testing

import app.petitesroues.android.desktop.Nsd
import app.petitesroues.android.di.PlatformModule
import app.petitesroues.android.library.AppFolder
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/**
 * [PlatformModule] for Hilt tests: no All files access, and a local network where no desktop
 * announces itself.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PlatformModule::class])
object TestPlatformModule {
  @Provides fun appFolder(): AppFolder = FakeAppFolder()

  @Provides fun nsd(): Nsd = FakeNsd()
}
