package com.github.heartratemonitor_compose.service

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import com.github.heartratemonitor_compose.data.PrefsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 负责 GPS 速度采集与开关状态管理。
 *
 * 将位置监听器的注册/注销、速度单位转换从 [BleService] 中剥离，
 * [BleService] 只需通过 [speed] StateFlow 读取结果。
 */
class SpeedProvider(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _speed.value = if (location.hasSpeed()) {
                location.speed * 3.6f // m/s to km/h
            } else {
                0f
            }
        }

        // 兼容旧 API
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * 根据当前设置和权限重新评估是否需要请求位置更新。
     */
    fun update() {
        val isEnabled = prefs.getBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, false)
        val hasPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (isEnabled && hasPermission) {
            startLocationUpdates()
        } else {
            stopLocationUpdates()
            _speed.value = 0f
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
