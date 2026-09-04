package com.github.heartratemonitor_compose.service

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * startService / stopService 下沉到 service 层；Hilt 单例注入消费方，持有 applicationContext 不泄漏。
 */
@Singleton
class ServiceController @Inject constructor(@ApplicationContext context: Context) : ServiceLauncher {

    private val appContext = context.applicationContext

    override fun startBleService() {
        try {
            appContext.startService(Intent(appContext, BleService::class.java))
        } catch (_: Exception) {
            // ⚠️ 反直觉设计：后台 startService 可能被系统拒绝，忽略，由 recoverServices 兜底恢复
        }
    }

    override fun startStatusBarResidentService() {
        try {
            appContext.startService(Intent(appContext, StatusBarResidentService::class.java))
        } catch (_: Exception) {
            // 同上
        }
    }

    override fun stopStatusBarResidentService() {
        appContext.stopService(Intent(appContext, StatusBarResidentService::class.java))
    }

    override fun startHeartRateAlarmService() {
        try {
            appContext.startService(Intent(appContext, HeartRateAlarmService::class.java))
        } catch (_: Exception) {
            // 同上
        }
    }

    override fun stopHeartRateAlarmService() {
        appContext.stopService(Intent(appContext, HeartRateAlarmService::class.java))
    }
}
