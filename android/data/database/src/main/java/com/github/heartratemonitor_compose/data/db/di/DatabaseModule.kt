package com.github.heartratemonitor_compose.data.db.di

import android.content.Context
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceDao
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    fun provideHeartRateDao(db: AppDatabase): HeartRateDao = db.heartRateDao()

    @Provides
    fun provideFavoriteDeviceDao(db: AppDatabase): FavoriteDeviceDao = db.favoriteDeviceDao()
}
