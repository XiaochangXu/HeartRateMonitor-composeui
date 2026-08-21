package com.github.heartratemonitor_compose.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将 [Context.startService] / [Context.stopService] 调用从 UI 层下沉到 service 层。
 * Hilt 单例注入消费方（内部持有 applicationContext，不泄漏 Activity）。
 */
@Singleton
class ServiceController @Inject constructor(@ApplicationContext context: Context) : ServiceLauncher {

    private val appContext = context.applicationContext

    override fun startBleService() {
        try {
            appContext.startService(Intent(appContext, BleService::class.java))
        } catch (_: Exception) {
            // 后台 startService 可能被系统拒绝（BackgroundServiceStartNotAllowedException），
            // 捕获后忽略，用户进入前台时会通过 recoverServices 兜底恢复。
        }
    }

    override fun startStatusBarResidentService() {
        try {
            appContext.startService(Intent(appContext, StatusBarResidentService::class.java))
        } catch (_: Exception) {
            // 后台 startService 可能被系统拒绝，捕获后忽略。
        }
    }

    override fun stopStatusBarResidentService() {
        appContext.stopService(Intent(appContext, StatusBarResidentService::class.java))
    }

    override fun startHeartRateAlarmService() {
        try {
            appContext.startService(Intent(appContext, HeartRateAlarmService::class.java))
        } catch (_: Exception) {
            // 后台 startService 可能被系统拒绝，捕获后忽略。
        }
    }

    override fun stopHeartRateAlarmService() {
        appContext.stopService(Intent(appContext, HeartRateAlarmService::class.java))
    }
}