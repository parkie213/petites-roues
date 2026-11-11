package app.petitesroues.android.testing

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import app.petitesroues.android.data.IndexDao
import app.petitesroues.android.data.IndexDatabase
import app.petitesroues.android.data.ProgressDao
import app.petitesroues.android.data.ProgressDatabase
import app.petitesroues.android.di.ApplicationScope
import app.petitesroues.android.di.DataModule
import app.petitesroues.android.di.IoDispatcher
import app.petitesroues.documents.StudyDocumentCodec
import app.petitesroues.documents.StudyDocumentJson
import app.petitesroues.documents.StudyDocumentReader
import app.petitesroues.sync.DesktopClient
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** [DataModule] for Hilt tests: the same objects, with both databases in memory. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {
  @Provides
  @Singleton
  fun indexDatabase(@ApplicationContext context: Context): IndexDatabase =
    Room.inMemoryDatabaseBuilder(context, IndexDatabase::class.java).build()

  @Provides
  @Singleton
  fun progressDatabase(@ApplicationContext context: Context): ProgressDatabase =
    Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
      .allowMainThreadQueries()
      .build()

  @Provides fun indexDao(database: IndexDatabase): IndexDao = database.index()

  @Provides fun progressDao(database: ProgressDatabase): ProgressDao = database.progress()

  @Provides
  fun contentResolver(@ApplicationContext context: Context): ContentResolver =
    context.contentResolver

  @Provides
  @Singleton
  fun studyDocumentCodec(): StudyDocumentCodec = StudyDocumentCodec(StudyDocumentJson.newMapper())

  @Provides
  @Singleton
  fun studyDocumentReader(codec: StudyDocumentCodec): StudyDocumentReader =
    StudyDocumentReader(codec)

  @Provides @Singleton fun desktopClient(): DesktopClient = FakeDesktop()

  @Provides
  @Singleton
  @ApplicationScope
  fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())

  @Provides @IoDispatcher fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
