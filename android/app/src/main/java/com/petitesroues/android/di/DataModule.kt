package com.petitesroues.android.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.petitesroues.android.data.IndexDao
import com.petitesroues.android.data.IndexDatabase
import com.petitesroues.android.data.ProgressDao
import com.petitesroues.android.data.ProgressDatabase
import com.petitesroues.documents.StudyDocumentReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** A scope that lives as long as the app process, for work that must outlast the screen that started it. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

/** The app's databases and the platform and shared-library objects that need providing. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
  /** A cache: any schema change drops and recreates it, and the library refreshes. */
  @Provides
  @Singleton
  fun indexDatabase(@ApplicationContext context: Context): IndexDatabase =
    Room.databaseBuilder(context, IndexDatabase::class.java, IndexDatabase.NAME)
      .fallbackToDestructiveMigration(dropAllTables = true)
      .build()

  /** User data: a schema change without a declared migration fails loudly rather than wiping it. */
  @Provides
  @Singleton
  fun progressDatabase(@ApplicationContext context: Context): ProgressDatabase =
    Room.databaseBuilder(context, ProgressDatabase::class.java, ProgressDatabase.NAME).build()

  @Provides fun indexDao(database: IndexDatabase): IndexDao = database.index()

  @Provides fun progressDao(database: ProgressDatabase): ProgressDao = database.progress()

  @Provides fun contentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

  @Provides @Singleton fun studyDocumentReader(): StudyDocumentReader = StudyDocumentReader()

  @Provides
  @Singleton
  @ApplicationScope
  fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
