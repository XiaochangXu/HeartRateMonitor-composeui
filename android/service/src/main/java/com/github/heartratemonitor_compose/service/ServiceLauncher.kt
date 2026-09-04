package com.github.heartratemonitor_compose.service

/**
 * 将 startService/stopService 抽象为接口，ViewModel/UI 依赖本接口便于单测注入 fake 实现。
 */
interface ServiceLauncher {

    fun startBleService()

    fun startStatusBarResidentService()

    fun stopStatusBarResidentService()

    fun startHeartRateAlarmService()

    fun stopHeartRateAlarmService()
}
