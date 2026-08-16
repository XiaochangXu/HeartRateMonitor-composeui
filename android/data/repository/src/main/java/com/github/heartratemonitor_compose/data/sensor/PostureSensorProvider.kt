package com.github.heartratemonitor_compose.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将 [SensorManager] 的注册/注销逻辑从 UI 层下沉到数据层，
 * UI 仅通过回调接收采样数据与分类结果，降低生命周期和线程处理复杂度。
 */
@Singleton
class PostureSensorProvider @Inject constructor(@ApplicationContext context: Context) {

    private val applicationContext = context.applicationContext
    private val sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listener: SensorEventListener? = null
    private var classifyHandler: Handler? = null
    private var classifyRunnable: Runnable? = null

    fun start(
        onSample: (Float, Float, Float) -> Unit,
        onClassify: () -> Unit,
        classifyIntervalMs: Long = 200L
    ) {
        stop()

        // 设备无加速度传感器时直接返回，避免注册空监听器并启动无意义的分类定时器
        val sensor = accelerometer ?: return

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onSample(event.values[0], event.values[1], event.values[2])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = sensorListener
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)

        val handler = Handler(Looper.getMainLooper())
        classifyHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                onClassify()
                handler.postDelayed(this, classifyIntervalMs)
            }
        }
        classifyRunnable = runnable
        handler.post(runnable)
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        classifyRunnable?.let { classifyHandler?.removeCallbacks(it) }
        classifyHandler = null
        classifyRunnable = null
    }
}