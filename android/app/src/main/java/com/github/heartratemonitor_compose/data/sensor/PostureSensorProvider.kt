package com.github.heartratemonitor_compose.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * 姿态传感器数据提供者。
 *
 * 将 [SensorManager] 的注册/注销逻辑从 UI 层（[HeartRateAlarmScreen]）下沉到数据层，
 * UI 仅通过回调接收采样数据与分类结果，降低生命周期和线程处理复杂度。
 */
class PostureSensorProvider(context: Context) {

    private val applicationContext = context.applicationContext
    private val sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listener: SensorEventListener? = null
    private var classifyHandler: Handler? = null
    private var classifyRunnable: Runnable? = null

    /**
     * 开始监听加速度传感器。
     *
     * @param onSample 每次传感器采样回调（x, y, z）。
     * @param onClassify 周期性分类回调，由调用方决定如何消费结果。
     * @param classifyIntervalMs 分类周期，默认 200ms。
     */
    fun start(
        onSample: (Float, Float, Float) -> Unit,
        onClassify: () -> Unit,
        classifyIntervalMs: Long = 200L
    ) {
        stop()

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onSample(event.values[0], event.values[1], event.values[2])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = sensorListener
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)

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

    /**
     * 停止监听并清理 Handler 回调。
     */
    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        classifyRunnable?.let { classifyHandler?.removeCallbacks(it) }
        classifyHandler = null
        classifyRunnable = null
    }
}
