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

    @Volatile
    var disconnectWebSocketClients: (() -> Unit)? = null
}
