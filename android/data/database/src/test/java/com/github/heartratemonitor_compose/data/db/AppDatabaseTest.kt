package com.github.heartratemonitor_compose.data.db

import androidx.room3.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.SupportSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [AppDatabase] 集成测试。
 *
 * 验证：
 * - 数据库创建与 DAO 获取
 * - Session CRUD：插入、结束、查询开启中会话、获取最后记录时间戳
 * - Record CRUD：单条插入、批量插入、按会话查询
 * - FavoriteDevice CRUD：插入、删除、批量查询
 * - 级联删除：删除 Session 后 Record 也被删除
 * - 统计聚合查询
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var heartRateDao: HeartRateDao
    private lateinit var favoriteDeviceDao: FavoriteDeviceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // 使用 Room 默认驱动：RoomOpenHelper 自行负责建表与版本管理。
        // 此前手工提供 SupportSQLiteOpenHelper + 空 onCreate Callback 的方式，
        // 版本与 AppDatabase.version 不一致时会走迁移/校验路径导致测试全挂。
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .build()
        heartRateDao = database.heartRateDao()
        favoriteDeviceDao = database.favoriteDeviceDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // ── Session CRUD ──

    @Test
    fun `insert session returns valid id`() = runTest {
        val sessionId = heartRateDao.insertSession(
            HeartRateSession(deviceName = "Device A", startTime = 1000L)
        )
        assertThat(sessionId).isGreaterThan(0L)
    }

    @Test
    fun `end session sets endTime`() = runTest {
        val sessionId = heartRateDao.insertSession(
            HeartRateSession(deviceName = "Device A", startTime = 1000L)
        )
        heartRateDao.endSession(sessionId, 5000L)

        val openSessions = heartRateDao.getOpenSessions()
        assertThat(openSessions).isEmpty()
    }

    @Test
    fun `getOpenSessions returns only sessions with null endTime`() = runTest {
        val s1 = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        val s2 = heartRateDao.insertSession(HeartRateSession(deviceName = "B", startTime = 2000L))
        heartRateDao.endSession(s1, 1500L)

        val openSessions = heartRateDao.getOpenSessions()
        assertThat(openSessions).hasSize(1)
        assertThat(openSessions[0].deviceName).isEqualTo("B")
    }

    @Test
    fun `getLastRecordTimestampForSession returns null when no records`() = runTest {
        val sessionId = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        val timestamp = heartRateDao.getLastRecordTimestampForSession(sessionId)
        assertThat(timestamp).isNull()
    }

    @Test
    fun `getLastRecordTimestampForSession returns max timestamp`() = runTest {
        val sessionId = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 1000L, heartRate = 70))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 3000L, heartRate = 80))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 2000L, heartRate = 75))

        val maxTimestamp = heartRateDao.getLastRecordTimestampForSession(sessionId)
        assertThat(maxTimestamp).isEqualTo(3000L)
    }

    // ── Record CRUD ──

    @Test
    fun `insert single record and query by session`() = runTest {
        val sessionId = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 1000L, heartRate = 70))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 2000L, heartRate = 80))

        val records = heartRateDao.getRecordsForSession(sessionId)
        assertThat(records).hasSize(2)
        // 排序 ASC by timestamp
        assertThat(records[0].heartRate).isEqualTo(70)
        assertThat(records[1].heartRate).isEqualTo(80)
    }

    @Test
    fun `insert batch records`() = runTest {
        val sessionId = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        val records = listOf(
            HeartRateRecord(sessionId = sessionId, timestamp = 1000L, heartRate = 60),
            HeartRateRecord(sessionId = sessionId, timestamp = 2000L, heartRate = 65),
            HeartRateRecord(sessionId = sessionId, timestamp = 3000L, heartRate = 70),
            HeartRateRecord(sessionId = sessionId, timestamp = 4000L, heartRate = 75),
            HeartRateRecord(sessionId = sessionId, timestamp = 5000L, heartRate = 80)
        )
        heartRateDao.insertRecords(records)

        val queried = heartRateDao.getRecordsForSession(sessionId)
        assertThat(queried).hasSize(5)
    }

    @Test
    fun `getHeartRatesForSession returns only heart rate values`() = runTest {
        val sessionId = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 1000L, heartRate = 60))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 2000L, heartRate = 70))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 3000L, heartRate = 80))

        val rates = heartRateDao.getHeartRatesForSession(sessionId)
        assertThat(rates).hasSize(3)
        assertThat(rates[0]).isEqualTo(60)
        assertThat(rates[1]).isEqualTo(70)
        assertThat(rates[2]).isEqualTo(80)
    }

    // ── 级联删除 ──

    @Test
    fun `deleting session cascades to records`() = runTest {
        val sessionId = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 1000L, heartRate = 70))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = sessionId, timestamp = 2000L, heartRate = 80))

        heartRateDao.deleteSession(sessionId)

        val records = heartRateDao.getRecordsForSession(sessionId)
        assertThat(records).isEmpty()
    }

    @Test
    fun `deleteSessionsByIds deletes multiple sessions`() = runTest {
        val s1 = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        val s2 = heartRateDao.insertSession(HeartRateSession(deviceName = "B", startTime = 2000L))
        val s3 = heartRateDao.insertSession(HeartRateSession(deviceName = "C", startTime = 3000L))

        heartRateDao.deleteSessionsByIds(listOf(s1, s3))

        val openSessions = heartRateDao.getOpenSessions()
        assertThat(openSessions).hasSize(1)
        assertThat(openSessions[0].deviceName).isEqualTo("B")
    }

    // ── 统计聚合 ──

    @Test
    fun `getAllSessionStats aggregates correctly`() = runTest {
        val s1 = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        val s2 = heartRateDao.insertSession(HeartRateSession(deviceName = "B", startTime = 2000L))

        // Session 1: 3 records
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s1, timestamp = 1000L, heartRate = 60))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s1, timestamp = 2000L, heartRate = 80))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s1, timestamp = 3000L, heartRate = 70))

        // Session 2: 2 records
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s2, timestamp = 2000L, heartRate = 100))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s2, timestamp = 3000L, heartRate = 110))

        val stats = heartRateDao.getAllSessionStats()
        assertThat(stats).hasSize(2)

        val s1Stats = stats.find { it.sessionId == s1 }
        assertThat(s1Stats).isNotNull()
        assertThat(s1Stats!!.recordCount).isEqualTo(3)
        assertThat(s1Stats.avgHeartRate).isEqualTo(70)  // (60+80+70)/3 = 70
        assertThat(s1Stats.maxHeartRate).isEqualTo(80)
        assertThat(s1Stats.minHeartRate).isEqualTo(60)
        assertThat(s1Stats.firstTimestamp).isEqualTo(1000L)
        assertThat(s1Stats.lastTimestamp).isEqualTo(3000L)

        val s2Stats = stats.find { it.sessionId == s2 }
        assertThat(s2Stats).isNotNull()
        assertThat(s2Stats!!.recordCount).isEqualTo(2)
        assertThat(s2Stats.avgHeartRate).isEqualTo(105)  // (100+110)/2 = 105
        assertThat(s2Stats.maxHeartRate).isEqualTo(110)
        assertThat(s2Stats.minHeartRate).isEqualTo(100)
    }

    @Test
    fun `getAllSessionStats returns empty for no records`() = runTest {
        heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        val stats = heartRateDao.getAllSessionStats()
        assertThat(stats).isEmpty()
    }

    // ── FavoriteDevice CRUD ──

    @Test
    fun `insert and getAllRaw favorite device`() = runTest {
        val device = FavoriteDeviceEntity(id = "AA:BB:CC", name = "Device A", timestamp = 1000L)
        favoriteDeviceDao.insert(device)

        val all = favoriteDeviceDao.getAllRaw()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isEqualTo("AA:BB:CC")
        assertThat(all[0].name).isEqualTo("Device A")
    }

    @Test
    fun `insert replaces on conflict`() = runTest {
        favoriteDeviceDao.insert(FavoriteDeviceEntity(id = "AA:BB:CC", name = "Old Name", timestamp = 1000L))
        favoriteDeviceDao.insert(FavoriteDeviceEntity(id = "AA:BB:CC", name = "New Name", timestamp = 2000L))

        val all = favoriteDeviceDao.getAllRaw()
        assertThat(all).hasSize(1)
        assertThat(all[0].name).isEqualTo("New Name")
        assertThat(all[0].timestamp).isEqualTo(2000L)
    }

    @Test
    fun `deleteById removes specific device`() = runTest {
        favoriteDeviceDao.insert(FavoriteDeviceEntity(id = "dev1", name = "A", timestamp = 1000L))
        favoriteDeviceDao.insert(FavoriteDeviceEntity(id = "dev2", name = "B", timestamp = 2000L))

        favoriteDeviceDao.deleteById("dev1")

        val all = favoriteDeviceDao.getAllRaw()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isEqualTo("dev2")
    }

    @Test
    fun `deleteAll removes all devices`() = runTest {
        favoriteDeviceDao.insert(FavoriteDeviceEntity(id = "dev1", name = "A", timestamp = 1000L))
        favoriteDeviceDao.insert(FavoriteDeviceEntity(id = "dev2", name = "B", timestamp = 2000L))

        favoriteDeviceDao.deleteAll()

        val all = favoriteDeviceDao.getAllRaw()
        assertThat(all).isEmpty()
    }

    // ── 多 Session 数据隔离 ──

    @Test
    fun `records from different sessions are isolated`() = runTest {
        val s1 = heartRateDao.insertSession(HeartRateSession(deviceName = "A", startTime = 1000L))
        val s2 = heartRateDao.insertSession(HeartRateSession(deviceName = "B", startTime = 2000L))

        heartRateDao.insertRecord(HeartRateRecord(sessionId = s1, timestamp = 1000L, heartRate = 60))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s1, timestamp = 2000L, heartRate = 70))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s2, timestamp = 3000L, heartRate = 80))
        heartRateDao.insertRecord(HeartRateRecord(sessionId = s2, timestamp = 4000L, heartRate = 90))

        val s1Records = heartRateDao.getRecordsForSession(s1)
        val s2Records = heartRateDao.getRecordsForSession(s2)

        assertThat(s1Records).hasSize(2)
        assertThat(s2Records).hasSize(2)
        assertThat(s1Records.all { it.heartRate in listOf(60, 70) }).isTrue()
        assertThat(s2Records.all { it.heartRate in listOf(80, 90) }).isTrue()
    }
}
