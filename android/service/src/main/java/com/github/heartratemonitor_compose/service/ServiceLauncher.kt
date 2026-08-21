package com.github.heartratemonitor_compose.service

/**
 * 将 startService / stopService 能力从具体类 [ServiceController] 中抽象出来，
 * ViewModel/UI 依赖本接口而非 [ServiceController]，便于单元测试注入 fake 实现。
 */
interface ServiceLauncher {

    fun startBleService()

    fun startStatusBarResidentService()

    fun stopStatusBarResidentService()

    fun startHeartRateAlarmService()

    fun stopHeartRateAlarmService()
}
