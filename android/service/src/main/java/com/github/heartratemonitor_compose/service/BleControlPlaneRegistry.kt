package com.github.heartratemonitor_compose.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程级 BLE 控制面注册表：BleService 注册自身，UI 侧经此获取活服务引用。
 *
 * ⚠️ 反直觉设计：必须保持 @Singleton——多 Activity 下各 MainViewModel 实例若各自持引用，
 * 命令会发往已销毁的旧服务导致扫描/连接静默失效；由 [BleService] 生命周期维护注册。
 */
@Singleton
class BleControlPlaneRegistry @Inject constructor() {

    private val _manager = MutableStateFlow<BleConnectionManager?>(null)
    val manager: StateFlow<BleConnectionManager?> = _manager.asStateFlow()

    fun register(manager: BleConnectionManager) {
        _manager.value = manager
    }

    fun unregister(manager: BleConnectionManager) {
        _manager.compareAndSet(manager, null)
    }
}
