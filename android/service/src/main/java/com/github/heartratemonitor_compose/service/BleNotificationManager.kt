package com.github.heartratemonitor_compose.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.github.heartratemonitor_compose.service.R
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository

/**
 * 速度显示开启且持有定位权限时附加 FOREGROUND_SERVICE_TYPE_LOCATION，
 * 启动失败时降级为 CONNECTED_DEVICE 类型重试。
 */
class BleNotificationManager(
    private val service: Service,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val TAG = "BleNotificationManager"
        private const val CHANNEL_ID = "BleServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    /** 将宿主 Service 提升为前台服务（幂等：可在 onCreate / onStartCommand 重复调用）。 */
    fun startForeground() {
        val channelName = service.getString(R.string.notification_channel_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = service.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(service.getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_bluetooth_connected)
            .setOngoing(true)
            .build()

        var type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasLocationPermission = ActivityCompat.checkSelfPermission(
                service,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val isSpeedEnabled = settingsRepository.get(SettingsKeys.SPEED_DISPLAY_ENABLED)

            if (hasLocationPermission && isSpeedEnabled) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }

            try {
                ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.e(TAG, "无法启动带 Location 类型的前台服务，尝试降级启动", e)
                try {
                    val safeType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, safeType)
                } catch (e2: Exception) {
                    Log.e(TAG, "致命错误：无法启动前台服务", e2)
                }
            }
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }
}
