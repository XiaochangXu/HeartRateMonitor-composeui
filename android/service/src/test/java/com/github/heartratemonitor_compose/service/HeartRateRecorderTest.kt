package com.github.heartratemonitor_compose.service

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.preferences.core.edit
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var database: AppDatabase
    private lateinit var dao: HeartRateDao
    private lateinit var recorder: HeartRateRecorder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // DataStore 单例跨测试用例存活，需显式清空；
        // SharedPreferences 一并清空，避免 SharedPreferencesMigration 迁入残留键
        prefs = context.getSharedPreferences(SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settingsRepository = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))

        // 使用 Room 默认驱动：RoomOpenHelper 自行负责建表与版本管理。
        // 此前手工提供 SupportSQLiteOpenHelper + 空 onCreate Callback 的方式，
        // 版本与 AppDatabase.version 不一致时会走迁移/校验路径导致测试全挂。
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .build()
        dao = database.heartRateDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createRecorder(scope: TestScope, dao: HeartRateDao = this.dao) = HeartRateRecorder(
        settingsRepository = settingsRepository,
        dao = dao,
        scope = scope
    )

    private fun enableHistory(enabled: Boolean) {
        // 经 Repository 写入：乐观缓存更新保证后续同步读立即生效，异步落盘 DataStore
        settingsRepository.set(SettingsKeys.HISTORY_RECORDING_ENABLED, enabled)
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
        recorder.cancelFlushLoop()
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
        recorder.cancelFlushLoop()
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
        recorder.cancelFlushLoop()
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
        recorder.cancelFlushLoop()
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
        recorder.cancelFlushLoop()
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

    // ── 取消语义（结构化取消 + 数据可抢救）──

    /** 包装 DAO：insertRecords 挂起直到协程取消，模拟落盘中途被取消（服务关停场景）。 */
    private class HangingInsertDao(private val delegate: HeartRateDao) : HeartRateDao by delegate {
        override suspend fun insertRecords(records: List<HeartRateRecord>) {
            awaitCancellation()
        }
    }

    @Test
    fun `cancelled flush re-queues records and propagates cancellation`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this, dao = HangingInsertDao(dao))
        recorder.startSession("Device A")
        recorder.record(60, "Device A")
        recorder.record(70, "Device A")

        val flushJob = launch { recorder.flushPendingRecords() }
        runCurrent()
        // 取消发生在 insertRecords 挂起期间
        flushJob.cancel()
        flushJob.join()

        // 回归：取消时记录必须被放回缓冲区（可被后续 drain/endSession 抢救），
        // 而不是在 catch(Exception) 中被吞掉后丢失（旧实现把 CancellationException 当 IO 故障）
        val drained = recorder.drainPendingRecords()
        assertThat(drained).hasSize(2)
        assertThat(drained.map { it.heartRate }).containsExactly(60, 70)
        recorder.cancelFlushLoop()
    }

    /** 包装 DAO：endSession 抛异常，模拟 teardown 路径数据库异常。 */
    private class ThrowingEndSessionDao(private val delegate: HeartRateDao) : HeartRateDao by delegate {
        override suspend fun endSession(sessionId: Long, endTime: Long) {
            throw IllegalStateException("database closed")
        }
    }

    @Test
    fun `endSession dao failure does not propagate and session resets`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this, dao = ThrowingEndSessionDao(dao))
        recorder.startSession("Device A")

        // 回归：endSession 的 DAO 异常被就地消化（否则 teardown 路径异常逸出会崩溃进程）
        recorder.endSession()

        // 会话已复位：再次 startSession 能正常创建新会话
        val id = recorder.startSession("Device B")
        assertThat(id).isNotNull()
        recorder.endSession()
        recorder.cancelFlushLoop()
    }

    /** 包装 DAO：insertRecords 抛外键约束异常（会话被删除场景）。 */
    private class ConstraintFailureDao(private val delegate: HeartRateDao) : HeartRateDao by delegate {
        override suspend fun insertRecords(records: List<HeartRateRecord>) {
            throw SQLiteConstraintException("FOREIGN KEY constraint failed")
        }
    }

    @Test
    fun `flush with constraint failure resets session and does not re-queue`() = runTest {
        enableHistory(true)
        recorder = createRecorder(this, dao = ConstraintFailureDao(dao))
        recorder.startSession("Device A")
        recorder.record(60, "Device A")

        recorder.flushPendingRecords() // 不抛

        // 约束失败语义：本批丢弃（不 re-add），会话重置（后续记录归属新会话）
        assertThat(recorder.drainPendingRecords()).isEmpty()
        recorder.record(70, "Device A")
        recorder.flushPendingRecords()
        assertThat(recorder.drainPendingRecords()).isEmpty()
        recorder.cancelFlushLoop()
    }
}
