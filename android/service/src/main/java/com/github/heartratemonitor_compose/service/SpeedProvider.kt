package com.github.heartratemonitor_compose.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository

/**
 * 将位置监听器的注册/注销、速度单位转换从 [BleService] 中剥离。
 *
 * Phase 2（HeartRateRepository 迁移）：速度值写入进程级 [HeartRateRepository]（SSOT），
 * 本类仅保留 GPS 监听与单位转换逻辑，不再自持状态流。
 */
class SpeedProvider(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val heartRateRepository: HeartRateRepository
) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            heartRateRepository.updateSpeed(
                if (location.hasSpeed()) {
                    location.speed * 3.6f // m/s to km/h
                } else {
                    0f
                }
            )
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * 根据当前设置和权限重新评估是否需要请求位置更新。
     */
    fun update() {
        val isEnabled = settingsRepository.get(SettingsKeys.SPEED_DISPLAY_ENABLED)
        val hasPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (isEnabled && hasPermission) {
            startLocationUpdates()
        } else {
            stopLocationUpdates()
            heartRateRepository.updateSpeed(0f)
        }
    }

    /**
     * 服务销毁时彻底释放位置监听。
     */
    fun stop() {
        stopLocationUpdates()
    }

    private fun startLocationUpdates() {
        try {
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
            } else {
                Log.w("SpeedProvider", "设备不支持 GPS，无法获取速度信息")
            }
        } catch (e: Exception) {
            Log.e("SpeedProvider", "Location update failed", e)
        }
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(locationListener)
    }
}
