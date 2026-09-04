package com.github.heartratemonitor_compose.data.di

import android.content.Context
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import com.github.heartratemonitor_compose.data.settings.di.AppScope
import com.github.heartratemonitor_compose.service.di.AlarmPageIntents
import com.github.heartratemonitor_compose.service.di.FairMemoryPageIntents
import com.github.heartratemonitor_compose.ui.main.MainActivity
import com.github.heartratemonitor_compose.ui.page.AlarmActivity
import com.github.heartratemonitor_compose.ui.page.FairMemoryActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

// ()->Intent 由 :app 提供，使 :service 的 FairMemoryNotifier 不再 import MainActivity。
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideReopenAppIntent(
        @ApplicationContext context: Context
    ): @JvmSuppressWildcards () -> Intent =
        {
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

    @Provides
    fun provideSuppressHideForExternalLaunch(): @JvmSuppressWildcards (Boolean) -> Unit =
        { value -> MainActivity.setSuppressHideForExternalLaunch(value) }

    // 二级页直达须垫 MainActivity，返回键才能回主界面而非桌面
    @Provides
    @Singleton
    @AlarmPageIntents
    fun provideAlarmPageIntents(
        @ApplicationContext context: Context,
        reopenAppIntent: @JvmSuppressWildcards () -> Intent
    ): @JvmSuppressWildcards () -> Array<Intent> = {
        TaskStackBuilder.create(context)
            .addNextIntent(reopenAppIntent())
            .addNextIntent(Intent(context, AlarmActivity::class.java))
            .intents
    }

    @Provides
    @Singleton
    @FairMemoryPageIntents
    fun provideFairMemoryPageIntents(
        @ApplicationContext context: Context,
        reopenAppIntent: @JvmSuppressWildcards () -> Intent
    ): @JvmSuppressWildcards () -> Array<Intent> = {
        TaskStackBuilder.create(context)
            .addNextIntent(reopenAppIntent())
            .addNextIntent(Intent(context, FairMemoryActivity::class.java))
            .intents
    }
}
