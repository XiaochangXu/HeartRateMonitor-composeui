package com.github.heartratemonitor_compose.ble

import android.util.Log
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.characteristicOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import java.util.UUID
import java.util.concurrent.CancellationException

fun String.toUuid(): UUID = UUID.fromString(this)


class BleManager {

    private val scanner by lazy { Scanner() }

    fun scan(): Flow<Advertisement> = scanner.advertisements
        .catch { cause ->
            Log.e("BleManager", "扫描过程中发生错误", cause)
        }

   
    fun observeHeartRate(peripheral: Peripheral): Flow<HeartRateMeasurement> {
        val characteristic = characteristicOf(
            service = BleConstants.HEART_RATE_SERVICE_UUID,
            characteristic = BleConstants.HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID
        )

        return peripheral.observe(characteristic)
            .map { data -> parseHeartRateMeasurement(data) }
            .onCompletion { cause ->
                if (cause != null && cause !is CancellationException) {
                    Log.w("BleManager", "心率数据流异常终止", cause)
                } else {
                    Log.d("BleManager", "心率数据流正常结束")
                }
            }
    }

    /**
     * 解析标准心率测量特征值 (0x2A37) 的完整数据。
     *
     * 蓝牙 GATT HR Profile 规范:
     * - 字节0: flags
     *   - bit 0:    心率值格式 (0=UINT8, 1=UINT16)
     *   - bit 1-2:  传感器接触状态 (00/01=不支持, 10=未接触, 11=已接触)
     *   - bit 3:    是否包含累计能耗 (Energy Expended)
     *   - bit 4:    是否包含 RR-Interval
     * - 心率值: UINT8 或 UINT16
     * - (可选) 累计能耗: UINT16, 单位千卡
     * - (可选) RR-Interval: UINT16 序列, 单位 1/1024 秒 (相邻 R 波峰的时间间隔)
     *
     * RR-Interval 是逐拍 (beat-to-beat) 数据,可据此计算瞬时心率与 HRV,
     * 比设备上报的平均 BPM 分辨率更高。早期实现仅解析 BPM,丢失了 RR 数据。
     */
    internal fun parseHeartRateMeasurement(data: ByteArray): HeartRateMeasurement {
        if (data.isEmpty()) return HeartRateMeasurement.EMPTY

        val flag = data[0].toInt()
        val is16bit = (flag and 0x01) != 0

        val sensorContactStatus = (flag shr 1) and 0x03
        val sensorContactSupported = sensorContactStatus == 0b10 || sensorContactStatus == 0b11
        val sensorContact = sensorContactStatus == 0b11

        val hasEnergyExpended = (flag and 0x08) != 0
        val hasRrInterval = (flag and 0x10) != 0

        var index = 1
        val bpm = if (is16bit) {
            if (data.size >= 3) {
                (data[2].toInt() and 0xFF shl 8) or (data[1].toInt() and 0xFF)
            } else {
                0
            }
        } else {
            if (data.size >= 2) data[1].toInt() and 0xFF else 0
        }
        index = if (is16bit) 3 else 2

        var energyExpended: Int? = null
        if (hasEnergyExpended && data.size >= index + 2) {
            energyExpended = (data[index + 1].toInt() and 0xFF shl 8) or (data[index].toInt() and 0xFF)
            index += 2
        }

        val rrIntervals = mutableListOf<Float>()
        if (hasRrInterval) {
            while (data.size >= index + 2) {
                val raw = (data[index + 1].toInt() and 0xFF shl 8) or (data[index].toInt() and 0xFF)
                if (raw > 0) {
                    rrIntervals.add(raw / 1024f)
                }
                index += 2
            }
        }

        return HeartRateMeasurement(
            bpm = bpm,
            rrIntervals = rrIntervals,
            sensorContactSupported = sensorContactSupported,
            sensorContact = sensorContact,
            energyExpended = energyExpended
        )
    }
}
