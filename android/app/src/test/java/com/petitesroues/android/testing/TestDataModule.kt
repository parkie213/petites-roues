package com.petitesroues.android.testing

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.petitesroues.android.data.IndexDao
import com.petitesroues.android.data.IndexDatabase
import com.petitesroues.android.data.ProgressDao
import com.petitesroues.android.data.ProgressDatabase
import com.petitesroues.android.di.ApplicationScope
import com.petitesroues.android.di.DataModule
import com.petitesroues.android.di.IoDispatcher
import com.petitesroues.documents.StudyDocumentReader
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

  @Provides @Singleton fun studyDocumentReader(): StudyDocumentReader = StudyDocumentReader()

  @Provides
  @Singleton
  @ApplicationScope
  fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())

  @Provides @IoDispatcher fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
