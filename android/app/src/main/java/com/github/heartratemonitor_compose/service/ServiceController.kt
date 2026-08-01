package com.github.heartratemonitor_compose.service

import android.content.Context
import android.content.Intent

/**
 * UI 层服务启动/停止的统一入口。
 *
 * 将 [Context.startService] / [Context.stopService] 调用从 UI 层下沉到 service 层，
 * 悬浮窗权限等前置检查仍由调用方负责。
 */
object ServiceController {

    fun startBleService(context: Context) {
        context.startService(Intent(context, BleService::class.java))
    }

    fun startStatusBarResidentService(context: Context) {
        context.startService(Intent(context, StatusBarResidentService::class.java))
    }

    fun stopStatusBarResidentService(context: Context) {
        context.stopService(Intent(context, StatusBarResidentService::class.java))
    }

    fun startHeartRateAlarmService(context: Context) {
        context.startService(Intent(context, HeartRateAlarmService::class.java))
    }

    fun stopHeartRateAlarmService(context: Context) {
        context.stopService(Intent(context, HeartRateAlarmService::class.java))
    }
}
