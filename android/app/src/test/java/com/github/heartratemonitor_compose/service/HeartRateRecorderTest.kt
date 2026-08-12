package com.github.heartratemonitor_compose.service

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.SupportSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [HeartRateRecorder] 集成测试。
 *
 * 验证：
 * - 历史记录关闭时 startSession 返回 null
 * - 历史记录关闭时 record 不写入
 * - 历史记录开启时 startSession 创建会话
 * - record 懒创建会话（中途开启历史记录）
 * - flushPendingRecords 将缓冲区写入数据库
 * - endSession 结束会话并写入剩余记录
 * - 批量记录正确性
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeartRateRecorderTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var database: AppDatabase
    private lateinit var dao: HeartRateDao
    private lateinit var recorder: HeartRateRecorder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(SupportSQLiteDriver(factory.create(config)))
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .build()
        dao = database.heartRateDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createRecorder(scope: TestScope) = HeartRateRecorder(
        prefs = prefs,
        dao = dao,
        scope = scope
    )

    private fun enableHistory(enabled: Boolean) {
        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, enabled).apply()
    }

    // ── 历史记录关闭 ──

    @Test
    fun `startSession returns null when history disabled`() = runTest {
        enableHistory(false)
        recorder = createRecorder(this)
        val sessionId = recorder.startSession("Device A")
        assertThat(sessionId).isNull()
    }

    @Test
    fun `record does nothing when history disabled`() = runTest {
        enableHistory(false)
        recorder = createRecorder(this)
        recorder.record(75, "Device A")
        recorder.flushPendingRecords()

        val openSessions = dao.getOpenSessions()
        assertThat(openSessions).isEmpty()
    }

    // ── 历史记录开启 ──

    @Test
    fun `startSession creates session when history enabled`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        val sessionId = recorder.startSession("Device A")
        assertThat(sessionId).isNotNull()
        assertThat(sessionId).isGreaterThan(0L)

        val openSessions = dao.getOpenSessions()
        assertThat(openSessions).hasSize(1)
        assertThat(openSessions[0].deviceName).isEqualTo("Device A")
    }

    @Test
    fun `record lazily creates session when history enabled mid-stream`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)

        // 不调用 startSession，直接 record，应该懒创建会话
        recorder.record(75, "Device A")
        recorder.flushPendingRecords()

        val openSessions = dao.getOpenSessions()
        assertThat(openSessions).hasSize(1)

        val records = dao.getRecordsForSession(openSessions[0].id)
        assertThat(records).hasSize(1)
        assertThat(records[0].heartRate).isEqualTo(75)
    }

    // ── flushPendingRecords ──

    @Test
    fun `flushPendingRecords writes buffered records to database`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        recorder.startSession("Device A")

        recorder.record(60, "Device A")
        recorder.record(65, "Device A")
        recorder.record(70, "Device A")
        recorder.flushPendingRecords()

        val openSessions = dao.getOpenSessions()
        assertThat(openSessions).hasSize(1)
        val records = dao.getRecordsForSession(openSessions[0].id)
        assertThat(records).hasSize(3)
        assertThat(records.map { it.heartRate }).containsExactly(60, 65, 70).inOrder()
    }

    @Test
    fun `flushPendingRecords with empty buffer does nothing`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        recorder.startSession("Device A")

        // 不添加任何记录，flush 不应抛异常
        recorder.flushPendingRecords()

        val openSessions = dao.getOpenSessions()
        assertThat(openSessions).hasSize(1)
        val records = dao.getRecordsForSession(openSessions[0].id)
        assertThat(records).isEmpty()
    }

    @Test
    fun `flushPendingRecords clears buffer after flush`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        recorder.startSession("Device A")

        recorder.record(60, "Device A")
        recorder.flushPendingRecords()

        // 第二次 flush，缓冲区应已清空，不应写入重复数据
        recorder.record(70, "Device A")
        recorder.flushPendingRecords()

        val openSessions = dao.getOpenSessions()
        val records = dao.getRecordsForSession(openSessions[0].id)
        assertThat(records).hasSize(2)
        assertThat(records[0].heartRate).isEqualTo(60)
        assertThat(records[1].heartRate).isEqualTo(70)
    }

    // ── endSession ──

    @Test
    fun `endSession ends the current session`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        recorder.startSession("Device A")

        recorder.record(60, "Device A")
        recorder.record(70, "Device A")
        recorder.endSession()

        val openSessions = dao.getOpenSessions()
        assertThat(openSessions).isEmpty()
    }

    @Test
    fun `endSession flushes remaining records`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        recorder.startSession("Device A")

        recorder.record(60, "Device A")
        recorder.record(65, "Device A")
        recorder.record(70, "Device A")
        // 不手动 flush，endSession 应负责写入
        recorder.endSession()

        // 查询已结束会话的记录（需要通过 getAllSessionStats 或直接查询）
        val stats = dao.getAllSessionStats()
        assertThat(stats).hasSize(1)
        assertThat(stats[0].recordCount).isEqualTo(3)
    }

    @Test
    fun `endSession when no session active does not throw`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        // 未 startSession 直接 endSession
        recorder.endSession()
        // 不抛异常即通过
    }

    @Test
    fun `endSession when history disabled does not throw`() = runTest {
        enableHistory(false)
        recorder = createRecorder(this)
        recorder.endSession()
        // 不抛异常即通过
    }

    // ── 多会话场景 ──

    @Test
    fun `startSession after endSession creates new session`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)

        // 第一会话
        val s1 = recorder.startSession("Device A")
        recorder.record(60, "Device A")
        recorder.endSession()

        // 第二会话
        val s2 = recorder.startSession("Device B")
        recorder.record(80, "Device B")
        recorder.endSession()

        assertThat(s1).isNotNull()
        assertThat(s2).isNotNull()
        assertThat(s1).isNotEqualTo(s2)

        val stats = dao.getAllSessionStats()
        assertThat(stats).hasSize(2)
    }

    // ── cancelFlushLoop ──

    @Test
    fun `cancelFlushLoop does not throw`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        recorder.startSession("Device A")
        recorder.cancelFlushLoop()
        // 不抛异常即通过
    }

    @Test
    fun `records after cancelFlushLoop are flushed on endSession`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this)
        recorder.startSession("Device A")

        recorder.record(60, "Device A")
        recorder.cancelFlushLoop()
        recorder.record(70, "Device A")
        recorder.endSession()

        val stats = dao.getAllSessionStats()
        assertThat(stats).hasSize(1)
        assertThat(stats[0].recordCount).isEqualTo(2)
    }
}
