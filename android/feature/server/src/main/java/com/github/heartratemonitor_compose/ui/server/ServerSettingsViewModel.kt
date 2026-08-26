package com.github.heartratemonitor_compose.ui.server

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.network.IpAddressProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.service.LanTransferSharedState
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 服务器设置页面的 ViewModel
 *
 * 职责：
 * - 通过 [SettingsRepository] 的 observe StateFlow 合并出服务器设置快照，
 *   Flow 回流经 [setState] 归约进 UiState，UI 经 collectAsStateWithLifecycle
 *   只读收集（状态下行）。
 * - 开关与端口提交事件经 [ServerSettingsIntent] dispatch 上行写入
 *   [SettingsRepository]（事件上行）。
 *
 * IP 地址来源由构造注入的 [IpAddressProvider] 提供（原经 ServerDependencies EntryPoint 取用）。
 *
 * 服务器实际运行状态（端口冲突等导致启动失败）经 [LanTransferSharedState.serverRuntimeStatus]
 * 下行，UI 据此区分「设置已启用」与「服务器实际在运行」。
 */
@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val ipAddressProvider: IpAddressProvider,
    private val lanTransferSharedState: LanTransferSharedState
) : MviViewModel<ServerUiState, ServerSettingsIntent>(
    // 本机 IP 为进入页面时的一次性展示值，随初始状态快照归约（原 UI 层 remember 直读上提）
    initialServerUiState(settings, ipAddressProvider.getLocalIpAddress())
) {

    init {
        // SettingsRepository 构造期已预热快照，observe 首发射即真值；
        // 每次变化原子归约进 UiState，禁止本地双写（§3.5）
        //
        // combine collector 只管 enabled/port 四元组，完全不碰 httpRunning/wsRunning。
        // 运行状态由 serverRuntimeStatus collector 独家负责，
        // 避免 combine 与 serverRuntimeStatus 两个 collector 交错 setState 时
        // 互相覆盖 httpRunning/wsRunning（曾导致服务器已启动但 UI 卡在 false/null）。
        viewModelScope.launch {
            combine(
                settings.observe(SettingsKeys.HTTP_SERVER_ENABLED),
                settings.observe(SettingsKeys.HTTP_SERVER_PORT),
                settings.observe(SettingsKeys.WEBSOCKET_SERVER_ENABLED),
                settings.observe(SettingsKeys.WEBSOCKET_SERVER_PORT)
            ) { httpEnabled, httpPort, wsEnabled, wsPort ->
                ServerUiState(httpEnabled, httpPort, wsEnabled, wsPort)
            }.collect { snapshot ->
                // 保留 ipAddress 与 running 状态：combine 产出的 snapshot 不含这些字段，
                // 需从当前状态继承，否则初始 IP 会被 null 覆盖导致显示"未连接网络"
                setState {
                    it.copy(
                        httpEnabled = snapshot.httpEnabled,
                        httpPort = snapshot.httpPort,
                        wsEnabled = snapshot.wsEnabled,
                        wsPort = snapshot.wsPort
                    )
                }
            }
        }
        // 服务器实际运行状态（端口冲突等导致启动失败时与设置开关不同步）
        viewModelScope.launch {
            lanTransferSharedState.serverRuntimeStatus.collect { runtime ->
                setState {
                    it.copy(
                        httpRunning = runtime.httpRunning,
                        wsRunning = runtime.wsRunning
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: ServerSettingsIntent) {
        when (intent) {
            is ServerSettingsIntent.SetHttpEnabled -> {
                if (intent.enabled) {
                    // 在写入设置前立即将 httpRunning 重置为 null（启动中），
                    // 避免 ServerHost 更新状态回流前 UI 带着旧值 false（上次失败/关闭遗留）
                    // 闪现红色"启动失败"文字。
                    // 此处只改 httpRunning，不碰 httpEnabled——httpEnabled 由 combine 回流写入，
                    // 避免与 combine collector 双写竞争。
                    setState { it.copy(httpRunning = null) }
                    // 开启服务器即采用默认端口（保持旧页面"开关打开时默认端口立即落盘"语义）
                    settings.set(
                        SettingsKeys.HTTP_SERVER_PORT,
                        AppSettings.defaultFor(SettingsKeys.HTTP_SERVER_PORT)
                    )
                }
                settings.set(SettingsKeys.HTTP_SERVER_ENABLED, intent.enabled)
            }
            is ServerSettingsIntent.SetWsEnabled -> {
                if (intent.enabled) {
                    setState { it.copy(wsRunning = null) }
                    settings.set(
                        SettingsKeys.WEBSOCKET_SERVER_PORT,
                        AppSettings.defaultFor(SettingsKeys.WEBSOCKET_SERVER_PORT)
                    )
                }
                settings.set(SettingsKeys.WEBSOCKET_SERVER_ENABLED, intent.enabled)
            }
            is ServerSettingsIntent.CommitHttpPort ->
                commitPort(SettingsKeys.HTTP_SERVER_PORT, intent.text)
            is ServerSettingsIntent.CommitWsPort ->
                commitPort(SettingsKeys.WEBSOCKET_SERVER_PORT, intent.text)
        }
    }

    /**
     * 提交端口草稿。仅持久化合法端口值，非法输入（空/超范围）保持上次有效值——
     * 逐字符写入会让 BleSettingsListener 在输入中途反复重启服务器，故提交时机归 UI 层控制。
     */
    private fun commitPort(key: Preferences.Key<Int>, text: String) {
        text.toIntOrNull()?.takeIf { it in VALID_PORT_RANGE }
            ?.let { settings.set(key, it) }
    }

    companion object {
        private val VALID_PORT_RANGE = 1..65535
    }
}

/** 服务器设置页用户意图。 */
sealed interface ServerSettingsIntent {
    data class SetHttpEnabled(val enabled: Boolean) : ServerSettingsIntent
    data class SetWsEnabled(val enabled: Boolean) : ServerSettingsIntent
    data class CommitHttpPort(val text: String) : ServerSettingsIntent
    data class CommitWsPort(val text: String) : ServerSettingsIntent
}

/** 服务器设置 UI 状态（只读快照）。 */
data class ServerUiState(
    val httpEnabled: Boolean,
    val httpPort: Int,
    val wsEnabled: Boolean,
    val wsPort: Int,
    val ipAddress: String? = null,
    /** 服务器实际是否在运行：null=启动中, true=运行中, false=启动失败 */
    val httpRunning: Boolean? = null,
    val wsRunning: Boolean? = null
)

/**
 * 初始状态：读 [SettingsRepository] 内存快照真实值（app 启动时已预热、零 IO），
 * 消除进入页面时"先默认值后快照覆盖"的闪变；键缺失时 [SettingsRepository.get]
 * 回落 [AppSettings.DEFAULTS]（契约 10.3）。
 */
internal fun initialServerUiState(settings: SettingsRepository, ipAddress: String?): ServerUiState = ServerUiState(
    httpEnabled = settings.get(SettingsKeys.HTTP_SERVER_ENABLED),
    httpPort = settings.get(SettingsKeys.HTTP_SERVER_PORT),
    wsEnabled = settings.get(SettingsKeys.WEBSOCKET_SERVER_ENABLED),
    wsPort = settings.get(SettingsKeys.WEBSOCKET_SERVER_PORT),
    ipAddress = ipAddress
)
