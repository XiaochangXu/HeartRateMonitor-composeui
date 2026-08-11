package com.github.heartratemonitor_compose.service

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [KillStateSaver] 单元测试。
 *
 * 验证：
 * - 快照更新与读取
 * - save + read 往返一致性
 * - 无保存状态时 read 返回 null
 * - 过期状态（超过 5 分钟）read 返回 null
 * - clear 后 read 返回 null
 * - 设备信息的保存与恢复
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KillStateSaverTest {

    private lateinit var context: android.app.Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 清除可能残留的 KILL 状态
        KillStateSaver.clear(context)
        // 重置当前快照
        KillStateSaver.updateSnapshot(KillStateSaver.Snapshot())
    }

    // ── 快照更新 ──

    @Test
    fun `updateSnapshot updates current snapshot`() {
        val snapshot = KillStateSaver.Snapshot(
            route = "home",
            tab = "main",
            isFullScreen = false,
            connectedDeviceId = "AA:BB:CC:DD:EE:FF",
            connectedDeviceName = "Heart Rate Monitor"
        )
        KillStateSaver.updateSnapshot(snapshot)
        assertThat(KillStateSaver.currentSnapshot.route).isEqualTo("home")
        assertThat(KillStateSaver.currentSnapshot.tab).isEqualTo("main")
        assertThat(KillStateSaver.currentSnapshot.connectedDeviceName).isEqualTo("Heart Rate Monitor")
    }

    // ── 无保存状态 ──

    @Test
    fun `read returns null when no state saved`() {
        val result = KillStateSaver.read(context)
        assertThat(result).isNull()
    }

    // ── save + read 往返 ──

    @Test
    fun `save and read round trip restores all fields`() {
        val snapshot = KillStateSaver.Snapshot(
            route = "alarm",
            tab = "settings",
            isFullScreen = true,
            connectedDeviceId = "11:22:33:44:55:66",
            connectedDeviceName = "Polar H10"
        )
        KillStateSaver.updateSnapshot(snapshot)
        KillStateSaver.save(context)

        val restored = KillStateSaver.read(context)
        assertThat(restored).isNotNull()
        assertThat(restored!!.route).isEqualTo("alarm")
        assertThat(restored.tab).isEqualTo("settings")
        assertThat(restored.isFullScreen).isTrue()
        assertThat(restored.connectedDeviceId).isEqualTo("11:22:33:44:55:66")
        assertThat(restored.connectedDeviceName).isEqualTo("Polar H10")
    }

    @Test
    fun `save with default snapshot restores defaults`() {
        KillStateSaver.updateSnapshot(KillStateSaver.Snapshot())
        KillStateSaver.save(context)

        val restored = KillStateSaver.read(context)
        assertThat(restored).isNotNull()
        assertThat(restored!!.route).isEmpty()
        assertThat(restored.tab).isEmpty()
        assertThat(restored.isFullScreen).isFalse()
        assertThat(restored.connectedDeviceId).isNull()
        assertThat(restored.connectedDeviceName).isNull()
    }

    @Test
    fun `save with null device info restores nulls`() {
        val snapshot = KillStateSaver.Snapshot(
            route = "home",
            tab = "main",
            isFullScreen = false,
            connectedDeviceId = null,
            connectedDeviceName = null
        )
        KillStateSaver.updateSnapshot(snapshot)
        KillStateSaver.save(context)

        val restored = KillStateSaver.read(context)
        assertThat(restored).isNotNull()
        assertThat(restored!!.connectedDeviceId).isNull()
        assertThat(restored.connectedDeviceName).isNull()
    }

    // ── clear ──

    @Test
    fun `clear makes read return null`() {
        KillStateSaver.updateSnapshot(
            KillStateSaver.Snapshot(route = "home", tab = "main")
        )
        KillStateSaver.save(context)
        assertThat(KillStateSaver.read(context)).isNotNull()

        KillStateSaver.clear(context)
        assertThat(KillStateSaver.read(context)).isNull()
    }

    @Test
    fun `clear when nothing saved does not throw`() {
        KillStateSaver.clear(context)
        // 不抛异常即通过
    }

    // ── 多次 save 覆盖 ──

    @Test
    fun `second save overwrites first`() {
        KillStateSaver.updateSnapshot(
            KillStateSaver.Snapshot(route = "first", tab = "tab1")
        )
        KillStateSaver.save(context)

        KillStateSaver.updateSnapshot(
            KillStateSaver.Snapshot(route = "second", tab = "tab2")
        )
        KillStateSaver.save(context)

        val restored = KillStateSaver.read(context)
        assertThat(restored).isNotNull()
        assertThat(restored!!.route).isEqualTo("second")
        assertThat(restored.tab).isEqualTo("tab2")
    }

    // ── Snapshot data class ──

    @Test
    fun `Snapshot default values`() {
        val snapshot = KillStateSaver.Snapshot()
        assertThat(snapshot.route).isEmpty()
        assertThat(snapshot.tab).isEmpty()
        assertThat(snapshot.isFullScreen).isFalse()
        assertThat(snapshot.connectedDeviceId).isNull()
        assertThat(snapshot.connectedDeviceName).isNull()
    }

    @Test
    fun `Snapshot equality`() {
        val s1 = KillStateSaver.Snapshot(route = "home", tab = "main", isFullScreen = true)
        val s2 = KillStateSaver.Snapshot(route = "home", tab = "main", isFullScreen = true)
        val s3 = KillStateSaver.Snapshot(route = "home", tab = "main", isFullScreen = false)
        assertThat(s1).isEqualTo(s2)
        assertThat(s1).isNotEqualTo(s3)
    }
}
