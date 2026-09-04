package com.github.heartratemonitor_compose

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.github.heartratemonitor_compose.data.settings.di.AppScope
import com.github.heartratemonitor_compose.service.FairMemoryNotifier
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.github.heartratemonitor_compose.service.MemoryDiagnostics
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import com.github.heartratemonitor_compose.ui.theme.ThemePreviewCache
import com.github.heartratemonitor_compose.ui.theme.ThemeState
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class HeartRateApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var liquidGlassState: LiquidGlassState
    @Inject lateinit var fairMemoryReceiver: FairMemoryReceiver
    @Inject lateinit var fairMemoryNotifier: FairMemoryNotifier
    @Inject lateinit var memoryDiagnostics: MemoryDiagnostics
    @Inject lateinit var themePreviewCache: ThemePreviewCache
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope
    @Inject lateinit var appForegroundMonitor: AppForegroundMonitor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 显式触发：保证主题/液态玻璃配置在任何 Composable 读取前就绪。
        themeState
        liquidGlassState
        fairMemoryReceiver.initialize()
        fairMemoryNotifier.initialize()
        memoryDiagnostics.initialize()
        // 前台 Activity 计数：接管「退出应用隐藏后台」（最后一个页面停止即触发，零延迟）
        appForegroundMonitor.observe(this)
        // 后台预计算主题设置页所有预览色卡，避免首帧卡顿。
        themePreviewCache.preload(appScope)
    }
}
