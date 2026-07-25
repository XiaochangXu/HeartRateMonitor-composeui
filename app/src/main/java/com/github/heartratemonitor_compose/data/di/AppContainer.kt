package com.github.heartratemonitor_compose.data.di

import android.app.Application
import com.github.heartratemonitor_compose.data.repository.FavoriteDeviceRepository
import com.github.heartratemonitor_compose.data.repository.HistoryRepository
import com.github.heartratemonitor_compose.data.repository.SessionRepository
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.network.IpAddressProvider
import com.github.heartratemonitor_compose.data.sensor.PostureSensorProvider
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository

/**
 * 应用级依赖容器（手动 DI，避免引入 Hilt/Koin 等框架）。
 *
 * 职责：
 * - 持有 Repository、系统服务包装类等应用级单例。
 * - 供 ViewModel / Service 通过 [Application.appContainer] 获取依赖，
 *   替代分散的 `context.settingsRepository`、`AppDatabase.getDatabase()` 等直接构造。
 *
 * 不跨作用域缓存短生命周期对象（如 Activity、Peripheral），避免内存泄漏。
 */
class AppContainer(private val application: Application) {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(application) }

    val historyRepository: HistoryRepository by lazy { HistoryRepository(application) }

    val favoriteDeviceRepository: FavoriteDeviceRepository by lazy { FavoriteDeviceRepository }

    val sessionRepository: SessionRepository by lazy { SessionRepository }

    val webhookRepository: WebhookRepository by lazy { WebhookRepository(application) }

    val postureSensorProvider: PostureSensorProvider by lazy { PostureSensorProvider(application) }

    val ipAddressProvider: IpAddressProvider by lazy { IpAddressProvider(application) }

    val overlayPermissionProvider: OverlayPermissionProvider by lazy { OverlayPermissionProvider(application) }
}
