package com.github.heartratemonitor_compose.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SettingsRepository] 单元测试（类型化键 API）。
 *
 * 验证：
 * - 各类型（Boolean / String / Int / Float / Long）的读写正确性
 * - StateFlow observe 方法的初始值、更新通知与实例共享
 * - nullable String 的 null 值处理
 * - remove 方法
 * - AppSettings 快照的默认值解析与乐观更新
 *
 * 测试用键为临时键（不登记于 [AppSettings.DEFAULTS]），一律走显式默认值参数重载。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryTest {

    private lateinit var repo: SettingsRepository
    private lateinit var appContext: android.app.Application

    private val testBool = booleanPreferencesKey("test_bool")
    private val testObserveBool = booleanPreferencesKey("test_observe_bool")
    private val testSameBool = booleanPreferencesKey("test_same_bool")
    private val testStr = stringPreferencesKey("test_str")
    private val testObserveStr = stringPreferencesKey("test_observe_str")
    private val testNullStr = stringPreferencesKey("test_null_str")
    private val testNullObserve = stringPreferencesKey("test_null_observe")
    private val testInt = intPreferencesKey("test_int")
    private val testObserveInt = intPreferencesKey("test_observe_int")
    private val testNegInt = intPreferencesKey("test_neg_int")
    private val testFloat = floatPreferencesKey("test_float")
    private val testObserveFloat = floatPreferencesKey("test_observe_float")
    private val testLong = longPreferencesKey("test_long")
    private val testObserveLong = longPreferencesKey("test_observe_long")
    private val testRemove = booleanPreferencesKey("test_remove")

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        // 清除可能残留的数据：DataStore 单例跨测试用例存活，需显式清空；
        // SharedPreferences 一并清空，避免 SharedPreferencesMigration 迁入残留键
        appContext.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { appContext.settingsDataStore.edit { it.clear() } }
        // Unconfined 作用域：异步写在调用线程同步发起，配合写时乐观缓存更新，
        // 保持「写后立读」断言无需等待调度
        repo = SettingsRepository(appContext, CoroutineScope(Dispatchers.Unconfined))
    }

    /**
     * 等待指定键落盘且仓库完成对账发射处理。
     *
     * 连续两次 set 之间若第一次写的发射尚未对账完成，第二次写后的立即断言可能
     * 落入已文档化的瞬态回退窗口（SettingsRepository KDoc：旧快照发射短暂回退
     * 乐观值，下一次发射自愈）。observe 类测试在二次写入前调用本方法消除该竞态。
     */
    private fun awaitDiskReconciled(vararg keys: Preferences.Key<*>) {
        runBlocking {
            withTimeout(5000) {
                while (!keys.all { appContext.settingsDataStore.data.first().asMap().containsKey(it) }) {
                    delay(10)
                }
                // 落盘完成即发射已产生，再让出调度确保 Unconfined 收集者完成对账
                delay(50)
            }
        }
    }

    // ── Boolean ──

    @Test
    fun `boolean get returns default when not set`() {
        assertThat(repo.get(testBool, false)).isFalse()
        assertThat(repo.get(testBool, true)).isTrue()
    }

    @Test
    fun `boolean set and get`() {
        repo.set(testBool, true)
        assertThat(repo.get(testBool, false)).isTrue()

        repo.set(testBool, false)
        assertThat(repo.get(testBool, true)).isFalse()
    }

    @Test
    fun `observe emits current value and updates`() {
        repo.set(testObserveBool, false)
        val flow = repo.observe(testObserveBool, false)
        assertThat(flow.value).isFalse()

        awaitDiskReconciled(testObserveBool)
        repo.set(testObserveBool, true)
        assertThat(flow.value).isTrue()
    }

    @Test
    fun `observe returns same flow instance for same key`() {
        val flow1 = repo.observe(testSameBool, false)
        val flow2 = repo.observe(testSameBool, false)
        assertThat(flow1).isSameInstanceAs(flow2)
    }

    // ── String ──

    @Test
    fun `string get returns default when not set`() {
        assertThat(repo.get(testStr, "default")).isEqualTo("default")
    }

    @Test
    fun `string set and get`() {
        repo.set(testStr, "hello")
        assertThat(repo.get(testStr, "default")).isEqualTo("hello")
    }

    @Test
    fun `observe string emits current value and updates`() {
        repo.set(testObserveStr, "initial")
        val flow = repo.observe(testObserveStr, "default")
        assertThat(flow.value).isEqualTo("initial")

        awaitDiskReconciled(testObserveStr)
        repo.set(testObserveStr, "updated")
        assertThat(flow.value).isEqualTo("updated")
    }

    // ── Nullable String ──

    @Test
    fun `nullable string get returns null when not set`() {
        assertThat(repo.getNullable(testNullStr)).isNull()
    }

    @Test
    fun `nullable string set and get`() {
        repo.set(testNullStr, "value")
        assertThat(repo.getNullable(testNullStr)).isEqualTo("value")
    }

    @Test
    fun `observeNullable updates to null when key removed`() {
        repo.set(testNullObserve, "value")
        val flow = repo.observeNullable(testNullObserve)
        assertThat(flow.value).isEqualTo("value")

        awaitDiskReconciled(testNullObserve)
        repo.remove(testNullObserve)
        assertThat(flow.value).isNull()
    }

    // ── Int ──

    @Test
    fun `int get returns default when not set`() {
        assertThat(repo.get(testInt, 42)).isEqualTo(42)
    }

    @Test
    fun `int set and get`() {
        repo.set(testInt, 100)
        assertThat(repo.get(testInt, 0)).isEqualTo(100)
    }

    @Test
    fun `observe int emits current value and updates`() {
        repo.set(testObserveInt, 50)
        val flow = repo.observe(testObserveInt, 0)
        assertThat(flow.value).isEqualTo(50)

        awaitDiskReconciled(testObserveInt)
        repo.set(testObserveInt, 200)
        assertThat(flow.value).isEqualTo(200)
    }

    @Test
    fun `int negative values`() {
        repo.set(testNegInt, -1)
        assertThat(repo.get(testNegInt, 0)).isEqualTo(-1)
    }

    // ── Float ──

    @Test
    fun `float get returns default when not set`() {
        assertThat(repo.get(testFloat, 1.5f)).isEqualTo(1.5f)
    }

    @Test
    fun `float set and get`() {
        repo.set(testFloat, 3.14f)
        assertThat(repo.get(testFloat, 0f)).isWithin(0.001f).of(3.14f)
    }

    @Test
    fun `observe float emits current value and updates`() {
        repo.set(testObserveFloat, 2.5f)
        val flow = repo.observe(testObserveFloat, 0f)
        assertThat(flow.value).isWithin(0.001f).of(2.5f)

        awaitDiskReconciled(testObserveFloat)
        repo.set(testObserveFloat, 9.9f)
        assertThat(flow.value).isWithin(0.001f).of(9.9f)
    }

    // ── Long ──

    @Test
    fun `long get returns default when not set`() {
        assertThat(repo.get(testLong, 12345L)).isEqualTo(12345L)
    }

    @Test
    fun `long set and get`() {
        repo.set(testLong, 9876543210L)
        assertThat(repo.get(testLong, 0L)).isEqualTo(9876543210L)
    }

    @Test
    fun `observe long emits current value and updates`() {
        repo.set(testObserveLong, 1000L)
        val flow = repo.observe(testObserveLong, 0L)
        assertThat(flow.value).isEqualTo(1000L)

        awaitDiskReconciled(testObserveLong)
        repo.set(testObserveLong, 5000L)
        assertThat(flow.value).isEqualTo(5000L)
    }

    // ── Remove ──

    @Test
    fun `remove deletes the key`() {
        repo.set(testRemove, true)
        assertThat(repo.get(testRemove, false)).isTrue()

        repo.remove(testRemove)
        assertThat(repo.get(testRemove, false)).isFalse()
    }

    @Test
    fun `remove non-existent key does not throw`() {
        repo.remove(booleanPreferencesKey("non_existent_key"))
        // 不抛异常即通过
    }

    // ── AppSettings 快照 ──

    @Test
    fun `settings snapshot resolves defaults when keys absent`() {
        val snapshot = repo.settings.value
        assertThat(snapshot.historyRecordingEnabled).isFalse()
        assertThat(snapshot.heartbeatAnimationEnabled).isTrue()
        assertThat(snapshot.httpServerPort).isEqualTo(8000)
        assertThat(snapshot.websocketServerPort).isEqualTo(8001)
        assertThat(snapshot.favoriteDeviceId).isNull()
    }

    @Test
    fun `settings snapshot updates optimistically after set`() {
        repo.set(SettingsKeys.HTTP_SERVER_PORT, 9000)
        assertThat(repo.settings.value.httpServerPort).isEqualTo(9000)
    }

    // ── 实际 SettingsKeys 验证 ──

    @Test
    fun `real settings key - history recording enabled`() {
        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, true)
        assertThat(repo.get(SettingsKeys.HISTORY_RECORDING_ENABLED)).isTrue()
    }

    @Test
    fun `real settings key - alarm high threshold`() {
        repo.set(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, 120)
        assertThat(repo.get(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD)).isEqualTo(120)
    }
}
