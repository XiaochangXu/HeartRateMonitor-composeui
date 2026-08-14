package com.github.heartratemonitor_compose.data.repository

import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SettingsRepository] 单元测试。
 *
 * 验证：
 * - 各类型（Boolean / String / Int / Float / Long）的读写正确性
 * - StateFlow observe 方法的初始值与更新通知
 * - nullable String 的 null 值处理
 * - remove 方法
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryTest {

    private lateinit var repo: SettingsRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // 清除可能残留的数据
        context.getSharedPreferences(PrefsKeys.FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        repo = SettingsRepository(context)
    }

    // ── Boolean ──

    @Test
    fun `boolean get returns default when not set`() {
        assertThat(repo.getBoolean("test_bool", false)).isFalse()
        assertThat(repo.getBoolean("test_bool", true)).isTrue()
    }

    @Test
    fun `boolean set and get`() {
        repo.setBoolean("test_bool", true)
        assertThat(repo.getBoolean("test_bool", false)).isTrue()

        repo.setBoolean("test_bool", false)
        assertThat(repo.getBoolean("test_bool", true)).isFalse()
    }

    @Test
    fun `observeBoolean emits current value and updates`() {
        repo.setBoolean("test_observe_bool", false)
        val flow = repo.observeBoolean("test_observe_bool", false)
        assertThat(flow.value).isFalse()

        repo.setBoolean("test_observe_bool", true)
        assertThat(flow.value).isTrue()
    }

    @Test
    fun `observeBoolean returns same flow instance for same key`() {
        val flow1 = repo.observeBoolean("test_same_bool", false)
        val flow2 = repo.observeBoolean("test_same_bool", false)
        assertThat(flow1).isSameInstanceAs(flow2)
    }

    // ── String ──

    @Test
    fun `string get returns default when not set`() {
        assertThat(repo.getString("test_str", "default")).isEqualTo("default")
    }

    @Test
    fun `string set and get`() {
        repo.setString("test_str", "hello")
        assertThat(repo.getString("test_str", "default")).isEqualTo("hello")
    }

    @Test
    fun `observeString emits current value and updates`() {
        repo.setString("test_observe_str", "initial")
        val flow = repo.observeString("test_observe_str", "default")
        assertThat(flow.value).isEqualTo("initial")

        repo.setString("test_observe_str", "updated")
        assertThat(flow.value).isEqualTo("updated")
    }

    // ── Nullable String ──

    @Test
    fun `nullable string get returns null when not set`() {
        assertThat(repo.getStringNullable("test_null_str")).isNull()
        assertThat(repo.getStringNullable("test_null_str", "fallback")).isEqualTo("fallback")
    }

    @Test
    fun `nullable string set and get`() {
        repo.setString("test_null_str", "value")
        assertThat(repo.getStringNullable("test_null_str")).isEqualTo("value")
    }

    @Test
    fun `observeStringNullable updates to null when key removed`() {
        repo.setString("test_null_observe", "value")
        val flow = repo.observeStringNullable("test_null_observe")
        assertThat(flow.value).isEqualTo("value")

        repo.remove("test_null_observe")
        assertThat(flow.value).isNull()
    }

    // ── Int ──

    @Test
    fun `int get returns default when not set`() {
        assertThat(repo.getInt("test_int", 42)).isEqualTo(42)
    }

    @Test
    fun `int set and get`() {
        repo.setInt("test_int", 100)
        assertThat(repo.getInt("test_int", 0)).isEqualTo(100)
    }

    @Test
    fun `observeInt emits current value and updates`() {
        repo.setInt("test_observe_int", 50)
        val flow = repo.observeInt("test_observe_int", 0)
        assertThat(flow.value).isEqualTo(50)

        repo.setInt("test_observe_int", 200)
        assertThat(flow.value).isEqualTo(200)
    }

    @Test
    fun `int negative values`() {
        repo.setInt("test_neg_int", -1)
        assertThat(repo.getInt("test_neg_int", 0)).isEqualTo(-1)
    }

    // ── Float ──

    @Test
    fun `float get returns default when not set`() {
        assertThat(repo.getFloat("test_float", 1.5f)).isEqualTo(1.5f)
    }

    @Test
    fun `float set and get`() {
        repo.setFloat("test_float", 3.14f)
        assertThat(repo.getFloat("test_float", 0f)).isWithin(0.001f).of(3.14f)
    }

    @Test
    fun `observeFloat emits current value and updates`() {
        repo.setFloat("test_observe_float", 2.5f)
        val flow = repo.observeFloat("test_observe_float", 0f)
        assertThat(flow.value).isWithin(0.001f).of(2.5f)

        repo.setFloat("test_observe_float", 9.9f)
        assertThat(flow.value).isWithin(0.001f).of(9.9f)
    }

    // ── Long ──

    @Test
    fun `long get returns default when not set`() {
        assertThat(repo.getLong("test_long", 12345L)).isEqualTo(12345L)
    }

    @Test
    fun `long set and get`() {
        repo.setLong("test_long", 9876543210L)
        assertThat(repo.getLong("test_long", 0L)).isEqualTo(9876543210L)
    }

    @Test
    fun `observeLong emits current value and updates`() {
        repo.setLong("test_observe_long", 1000L)
        val flow = repo.observeLong("test_observe_long", 0L)
        assertThat(flow.value).isEqualTo(1000L)

        repo.setLong("test_observe_long", 5000L)
        assertThat(flow.value).isEqualTo(5000L)
    }

    // ── Remove ──

    @Test
    fun `remove deletes the key`() {
        repo.setBoolean("test_remove", true)
        assertThat(repo.getBoolean("test_remove", false)).isTrue()

        repo.remove("test_remove")
        assertThat(repo.getBoolean("test_remove", false)).isFalse()
    }

    @Test
    fun `remove non-existent key does not throw`() {
        repo.remove("non_existent_key")
        // 不抛异常即通过
    }

    // ── 实际 PrefsKeys 验证 ──

    @Test
    fun `real prefs key - history recording enabled`() {
        repo.setBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, true)
        assertThat(repo.getBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)).isTrue()
    }

    @Test
    fun `real prefs key - alarm high threshold`() {
        repo.setInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, 120)
        assertThat(repo.getInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, 100)).isEqualTo(120)
    }
}
