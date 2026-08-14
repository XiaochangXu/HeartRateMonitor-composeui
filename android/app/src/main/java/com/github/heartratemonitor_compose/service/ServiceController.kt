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
        try {
            context.startService(Intent(context, BleService::class.java))
        } catch (_: Exception) {
            // 后台 startService 可能被系统拒绝（BackgroundServiceStartNotAllowedException），
            // 捕获后忽略，用户进入前台时会通过 recoverServices 兜底恢复。
        }
    }

    fun startStatusBarResidentService(context: Context) {
        try {
            context.startService(Intent(context, StatusBarResidentService::class.java))
        } catch (_: Exception) {
            // 后台 startService 可能被系统拒绝，捕获后忽略。
        }
    }

    fun stopStatusBarResidentService(context: Context) {
        context.stopService(Intent(context, StatusBarResidentService::class.java))
    }

    fun startHeartRateAlarmService(context: Context) {
        try {
            context.startService(Intent(context, HeartRateAlarmService::class.java))
        } catch (_: Exception) {
            // 后台 startService 可能被系统拒绝，捕获后忽略。
        }
    }

    fun stopHeartRateAlarmService(context: Context) {
        context.stopService(Intent(context, HeartRateAlarmService::class.java))
    }
}
