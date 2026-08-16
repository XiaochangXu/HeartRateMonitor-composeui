package com.github.heartratemonitor_compose.data.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Hilt 设置装配模块（Phase 2 迁入）。
 *
 * - [DataStore] 仍经 [settingsDataStore] 顶层委托（全进程唯一实例），
 *   KillStateSaver / ServiceBootInitializer 两个契约 2 例外的直连不受影响。
 * - [SettingsRepository] 的协程作用域参数使用 [AppScope] Qualifier，
 *   绑定由 :app 的 AppModule 提供（SupervisorJob + Dispatchers.Default）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        @AppScope scope: CoroutineScope
    ): SettingsRepository = SettingsRepository(context, scope)
}
