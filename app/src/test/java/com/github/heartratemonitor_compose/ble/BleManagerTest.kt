package com.github.heartratemonitor_compose.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 心率测量特征值 (0x2A37) 解析逻辑单元测试。
 *
 * 蓝牙 GATT HR Profile 字节布局：
 * - byte 0: flags
 *   - bit 0:     HR 格式 (0=UINT8, 1=UINT16)
 *   - bit 1-2:   传感器接触 (00/01=不支持, 10=未接触, 11=已接触)
 *   - bit 3:     是否含累计能耗
 *   - bit 4:     是否含 RR-Interval
 * - 随后: 心率值 (UINT8 或 UINT16，小端序)
 * - 可选: 累计能耗 (UINT16，小端序)
 * - 可选: RR-Interval 序列 (UINT16，小端序，单位 1/1024 秒)
 */
class BleManagerTest {

    private val bleManager = BleManager()

    @Test
    fun `empty data returns EMPTY measurement`() {
        val result = bleManager.parseHeartRateMeasurement(byteArrayOf())
        assertEquals(HeartRateMeasurement.EMPTY, result)
    }

    @Test
    fun `UINT8 heart rate without extras`() {
        // flags=0x00: UINT8, 不支持接触检测, 无能耗, 无 RR
        // HR = 75 (0x4B)
        val data = byteArrayOf(0x00, 0x4B)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(75, result.bpm)
        assertFalse(result.sensorContactSupported)
        assertFalse(result.sensorContact)
        assertNull(result.energyExpended)
        assertTrue(result.rrIntervals.isEmpty())
    }

    @Test
    fun `UINT16 heart rate value`() {
        // flags=0x01: UINT16, 不支持接触检测, 无能耗, 无 RR
        // HR = 200 (0x00C8, 小端序: 0xC8, 0x00)
        val data = byteArrayOf(0x01, 0xC8.toByte(), 0x00)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(200, result.bpm)
        assertFalse(result.sensorContactSupported)
        assertNull(result.energyExpended)
        assertTrue(result.rrIntervals.isEmpty())
    }

    @Test
    fun `sensor contact supported and detected`() {
        // flags=0x06: UINT16, bit1-2=11 (支持+已接触), 无能耗, 无 RR
        // HR = 80 (0x0050, 小端序: 0x50, 0x00)
        val data = byteArrayOf(0x06, 0x50, 0x00)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(80, result.bpm)
        assertTrue(result.sensorContactSupported)
        assertTrue(result.sensorContact)
    }

    @Test
    fun `sensor contact supported but not detected`() {
        // flags=0x04: UINT16, bit1-2=10 (支持+未接触), 无能耗, 无 RR
        // HR = 60 (0x003C, 小端序: 0x3C, 0x00)
        val data = byteArrayOf(0x04, 0x3C, 0x00)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(60, result.bpm)
        assertTrue(result.sensorContactSupported)
        assertFalse(result.sensorContact)
    }

    @Test
    fun `UINT8 with energy expended`() {
        // flags=0x08: UINT8, 不支持接触, 有能耗, 无 RR
        // HR = 80 (0x50)
        // Energy = 1000 (0x03E8, 小端序: 0xE8, 0x03)
        val data = byteArrayOf(0x08, 0x50, 0xE8.toByte(), 0x03)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(80, result.bpm)
        assertEquals(1000, result.energyExpended)
        assertTrue(result.rrIntervals.isEmpty())
    }

    @Test
    fun `UINT8 with single RR interval`() {
        // flags=0x10: UINT8, 不支持接触, 无能耗, 有 RR
        // HR = 60 (0x3C)
        // RR = 1024 (0x0400, 小端序: 0x00, 0x04) → 1024/1024 = 1.0s → 60 bpm
        val data = byteArrayOf(0x10, 0x3C, 0x00, 0x04)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(60, result.bpm)
        assertEquals(1, result.rrIntervals.size)
        assertEquals(1.0f, result.rrIntervals[0], 0.001f)
    }

    @Test
    fun `UINT8 with multiple RR intervals`() {
        // flags=0x10: UINT8, 不支持接触, 无能耗, 有 RR
        // HR = 80 (0x50)
        // RR1 = 1024 (0x0400, 小端: 0x00, 0x04) → 1.0s
        // RR2 = 1280 (0x0500, 小端: 0x00, 0x05) → 1.25s
        val data = byteArrayOf(0x10, 0x50, 0x00, 0x04, 0x00, 0x05)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(80, result.bpm)
        assertEquals(2, result.rrIntervals.size)
        assertEquals(1.0f, result.rrIntervals[0], 0.001f)
        assertEquals(1.25f, result.rrIntervals[1], 0.001f)
    }

    @Test
    fun `UINT8 with energy expended and RR intervals`() {
        // flags=0x18: UINT8, 不支持接触, 有能耗, 有 RR
        // HR = 80 (0x50)
        // Energy = 500 (0x01F4, 小端: 0xF4, 0x01)
        // RR = 1024 (0x0400, 小端: 0x00, 0x04) → 1.0s
        val data = byteArrayOf(0x18, 0x50, 0xF4.toByte(), 0x01, 0x00, 0x04)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(80, result.bpm)
        assertEquals(500, result.energyExpended)
        assertEquals(1, result.rrIntervals.size)
        assertEquals(1.0f, result.rrIntervals[0], 0.001f)
    }

    @Test
    fun `RR interval of zero is filtered out`() {
        // flags=0x10: UINT8, 有 RR
        // HR = 60 (0x3C)
        // RR = 0 (0x0000) → 应被过滤
        // RR = 1024 (0x0400) → 1.0s
        val data = byteArrayOf(0x10, 0x3C, 0x00, 0x00, 0x00, 0x04)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(60, result.bpm)
        assertEquals(1, result.rrIntervals.size)
        assertEquals(1.0f, result.rrIntervals[0], 0.001f)
    }

    @Test
    fun `UINT8 heart rate with truncated data returns zero bpm`() {
        // flags=0x00: UINT8, 但数据长度不足（只有 flags，没有 HR 字节）
        val data = byteArrayOf(0x00)
        val result = bleManager.parseHeartRateMeasurement(data)

        assertEquals(0, result.bpm)
    }
}
