package com.github.heartratemonitor_compose.service

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt 单例（唯一实例），BleService 经 @Inject 字段注入获取。
 * 必须保持 [Singleton]：BleService/ServerHost 写入、UI 读取——若两者拿到不同实例，
 * 连接数 StateFlow 与断开回调将彼此隔离，局域网传输功能失效。
 */
@Singleton
class LanTransferSharedState @Inject constructor() {

    val webSocketClientCount = MutableStateFlow(0)

    /**
     * 当前已连接的 WebSocket 客户端（PC）信息。
     * 无连接时为 null；有连接时携带从 User-Agent 解析的 OS 名称与 remoteIpAddress。
     * 由 WebSocketServerManager 写入，LanTransferViewModel 读取。
     */
    val connectedClientInfo = MutableStateFlow<ConnectedClientInfo?>(null)

    @Volatile
    var disconnectWebSocketClients: (() -> Unit)? = null

    /**
     * 服务器实际运行状态（与用户设置开关区分）。
     * - 设置开关 = 用户「想不想启用」
     * - 运行状态 = 服务器「是否真正在监听端口」
     *
     * 端口冲突等 IO 错误会导致设置开关为 true 但运行状态为 false，
     * UI 据此向用户展示「启动失败」而非误导性的「已启用」。
     *
     * 由 ServerHost 写入，ServerSettingsViewModel 读取。
     */
    val serverRuntimeStatus = MutableStateFlow(ServerRuntimeStatus())
}

/**
 * 服务器实际运行状态快照。
 *
 * 运行状态为三态：
 * - null  = 未知/启动中（UI 不显示错误，避免开关打开→服务器启动完成之间的瞬态闪烁）
 * - true  = 服务器正在监听端口
 * - false = 启动失败（端口冲突等）
 */
data class ServerRuntimeStatus(
    val httpRunning: Boolean? = null,
    val wsRunning: Boolean? = null
)

/**
 * 已连接的 WebSocket 客户端（PC）信息。
 *
 * @param name 从 HTTP User-Agent 头解析出的操作系统名称（如 "Windows"、"macOS"），
 *             解析失败时回退为 "PC"。
 * @param ip 客户端的局域网 IP 地址（NanoHTTPD IHTTPSession.remoteIpAddress）。
 */
data class ConnectedClientInfo(
    val name: String,
    val ip: String
)
