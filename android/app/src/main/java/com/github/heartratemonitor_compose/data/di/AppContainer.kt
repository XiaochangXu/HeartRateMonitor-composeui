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
import kotlinx.coroutines.flow.MutableStateFlow

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

    // ── 局域网传输：WebSocket 客户端（PC）连接数 ──
    // 由 BleService → ServerHost → WebSocketServerManager 在客户端 connect/disconnect 时更新。
    // UI 通过此值判断「已连接电脑设备」状态，替代易失的内存态与易残留的持久化偏好。
    // >0 表示有 PC 正在连接并接收心率推送。
    val webSocketClientCount = MutableStateFlow(0)

    // 断开所有 WebSocket 客户端连接的回调，由 BleService 在 onCreate 时注册、onDestroy 时清空。
    // 服务未运行时为 null，UI 调用为空操作（无连接可断）。
    @Volatile
    var disconnectWebSocketClients: (() -> Unit)? = null
}
