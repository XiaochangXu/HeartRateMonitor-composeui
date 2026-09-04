package com.github.heartratemonitor_compose.service

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt 单例，唯一实例。
 *
 * ⚠️ 反直觉设计：必须保持 @Singleton——BleService/ServerHost 写入、UI 读取，不同实例则 WS 连接数与断开回调彼此隔离。
 */
@Singleton
class LanTransferSharedState @Inject constructor() {

    val webSocketClientCount = MutableStateFlow(0)

    /**
     * 当前已连接的 WebSocket 客户端（PC）信息。
     * 由 WebSocketServerManager 写入，LanTransferViewModel 读取。
     */
    val connectedClientInfo = MutableStateFlow<ConnectedClientInfo?>(null)

    @Volatile
    var disconnectWebSocketClients: (() -> Unit)? = null

    /**
     * 服务器实际运行状态（与用户设置开关区分）。
     *
     * ⚠️ 反直觉设计：三态（null=启动中/true=监听中/false=失败）——
     * 端口冲突等 IO 错误会导致设置开关为 true 但运行状态为 false，UI 据此展示真实状态。
     */
    val serverRuntimeStatus = MutableStateFlow(ServerRuntimeStatus())
}

/**
 * null = 启动中（UI 不显示错误，避免开关打开→启动完成的瞬态闪烁）；true = 监听中；false = 失败。
 */
data class ServerRuntimeStatus(
    val httpRunning: Boolean? = null,
    val wsRunning: Boolean? = null
)

data class ConnectedClientInfo(
    val name: String,
    val ip: String
)
