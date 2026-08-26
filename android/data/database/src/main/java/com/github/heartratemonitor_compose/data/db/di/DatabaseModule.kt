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

/**
 * Hilt 数据库装配模块（Phase 2 迁入）。
 *
 * 内部仍调用 [AppDatabase.getDatabase] 作为构建函数（既有 DCL 单例，非新增；
 * 运行时唯一实例实际由 Hilt @Singleton 管理，语义等价）。
 */
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
