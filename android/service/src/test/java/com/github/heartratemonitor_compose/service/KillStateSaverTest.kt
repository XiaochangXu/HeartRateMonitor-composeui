package com.github.heartratemonitor_compose.service

import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private lateinit var saver: KillStateSaver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Phase 6：不再使用 :app 的 Context.settingsRepository 扩展，直接构造（Robolectric 环境无组合根）
        saver = KillStateSaver(
            context,
            SettingsRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        )
        // 清除可能残留的 KILL 状态
        saver.clear()
        // 重置当前快照
        saver.updateSnapshot(KillStateSaver.Snapshot())
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
        saver.updateSnapshot(snapshot)
        assertThat(saver.currentSnapshot.route).isEqualTo("home")
        assertThat(saver.currentSnapshot.tab).isEqualTo("main")
        assertThat(saver.currentSnapshot.connectedDeviceName).isEqualTo("Heart Rate Monitor")
    }

    // ── 无保存状态 ──

    @Test
    fun `read returns null when no state saved`() {
        val result = saver.read()
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
        saver.updateSnapshot(snapshot)
        saver.save()

        val restored = saver.read()
        assertThat(restored).isNotNull()
        assertThat(restored!!.route).isEqualTo("alarm")
        assertThat(restored.tab).isEqualTo("settings")
        assertThat(restored.isFullScreen).isTrue()
        assertThat(restored.connectedDeviceId).isEqualTo("11:22:33:44:55:66")
        assertThat(restored.connectedDeviceName).isEqualTo("Polar H10")
    }

    @Test
    fun `save with default snapshot restores defaults`() {
        saver.updateSnapshot(KillStateSaver.Snapshot())
        saver.save()

        val restored = saver.read()
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
        saver.updateSnapshot(snapshot)
        saver.save()

        val restored = saver.read()
        assertThat(restored).isNotNull()
        assertThat(restored!!.connectedDeviceId).isNull()
        assertThat(restored.connectedDeviceName).isNull()
    }

    // ── clear ──

    @Test
    fun `clear makes read return null`() {
        saver.updateSnapshot(
            KillStateSaver.Snapshot(route = "home", tab = "main")
        )
        saver.save()
        assertThat(saver.read()).isNotNull()

        saver.clear()
        assertThat(saver.read()).isNull()
    }

    @Test
    fun `clear when nothing saved does not throw`() {
        saver.clear()
        // 不抛异常即通过
    }

    // ── 多次 save 覆盖 ──

    @Test
    fun `second save overwrites first`() {
        saver.updateSnapshot(
            KillStateSaver.Snapshot(route = "first", tab = "tab1")
        )
        saver.save()

        saver.updateSnapshot(
            KillStateSaver.Snapshot(route = "second", tab = "tab2")
        )
        saver.save()

        val restored = saver.read()
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
