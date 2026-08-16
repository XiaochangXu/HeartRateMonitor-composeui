package com.github.heartratemonitor_compose.data.repository

import androidx.datastore.preferences.core.edit
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceDao
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FavoriteDeviceRepository] 旧收藏迁移逻辑测试。
 *
 * 回归覆盖：迁移失败（JSON 损坏）不得置位完成标志——否则旧数据永久丢失且永不重试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FavoriteDeviceRepositoryTest {

    private lateinit var context: android.app.Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dao: FavoriteDeviceDao
    private lateinit var repository: FavoriteDeviceRepository
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 清空 DataStore 与 SharedPreferences，避免跨用例残留
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settingsRepository = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))

        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .build()
        dao = database.favoriteDeviceDao()
        repository = FavoriteDeviceRepository(settingsRepository, dao)
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun legacyJson(vararg items: String): String = "[${items.joinToString(",")}]"

    private fun legacyItem(id: String, name: String, timestamp: Long = 1000L) =
        """{"id":"$id","name":"$name","timestamp":$timestamp}"""

    /**
     * 等待指定键落盘且 SettingsRepository 完成对账发射处理。
     *
     * 消除已文档化的瞬态回退竞态（SettingsRepository KDoc）：若前置 set 的
     * DataStore 发射在后续乐观写入之后才被对账，旧快照会短暂回退乐观值。
     */
    private fun awaitDiskReconciled(vararg keys: androidx.datastore.preferences.core.Preferences.Key<*>) {
        runBlocking {
            withTimeout(5000) {
                while (!keys.all { context.settingsDataStore.data.first().asMap().containsKey(it) }) {
                    delay(10)
                }
                // 落盘完成即发射已产生，再让出调度确保 Unconfined 收集者完成对账
                delay(50)
            }
        }
    }

    @Test
    fun `migration inserts favorites and sets completed flag`() = runTest {
        settingsRepository.set(
            SettingsKeys.FAVORITE_DEVICE_HISTORY,
            legacyJson(legacyItem("AA:BB", "Watch A"), legacyItem("CC:DD", "Watch B"))
        )
        awaitDiskReconciled(SettingsKeys.FAVORITE_DEVICE_HISTORY)

        repository.migrateLegacyFavoritesIfNeeded()

        val favorites = dao.getAll().first()
        assertThat(favorites).hasSize(2)
        assertThat(favorites.map { it.id }).containsExactly("AA:BB", "CC:DD")
        assertThat(settingsRepository.get(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM)).isTrue()
    }

    @Test
    fun `corrupt json does not set flag and migration retries next launch`() = runTest {
        settingsRepository.set(SettingsKeys.FAVORITE_DEVICE_HISTORY, "not-a-json-{{")
        awaitDiskReconciled(SettingsKeys.FAVORITE_DEVICE_HISTORY)

        repository.migrateLegacyFavoritesIfNeeded()

        // 回归：失败不置位完成标志
        assertThat(settingsRepository.get(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM)).isFalse()
        assertThat(dao.getAll().first()).isEmpty()

        // 修复数据后（等价下次启动）重试成功
        settingsRepository.set(
            SettingsKeys.FAVORITE_DEVICE_HISTORY,
            legacyJson(legacyItem("AA:BB", "Watch A"))
        )
        awaitDiskReconciled(SettingsKeys.FAVORITE_DEVICE_HISTORY)
        repository.migrateLegacyFavoritesIfNeeded()
        assertThat(settingsRepository.get(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM)).isTrue()
        assertThat(dao.getAll().first()).hasSize(1)
    }

    @Test
    fun `single corrupt entry is skipped and rest of migration completes`() = runTest {
        // 中间一条缺 name 字段（解析失败），前后两条正常
        val corrupt = """{"id":"AA:BB","timestamp":1000}"""
        settingsRepository.set(
            SettingsKeys.FAVORITE_DEVICE_HISTORY,
            legacyJson(legacyItem("AA:BB", "Watch A"), corrupt, legacyItem("CC:DD", "Watch B"))
        )
        awaitDiskReconciled(SettingsKeys.FAVORITE_DEVICE_HISTORY)

        repository.migrateLegacyFavoritesIfNeeded()

        val favorites = dao.getAll().first()
        assertThat(favorites).hasSize(2)
        assertThat(favorites.map { it.id }).containsExactly("AA:BB", "CC:DD")
        // 单条损坏不阻断整批迁移
        assertThat(settingsRepository.get(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM)).isTrue()
    }

    @Test
    fun `already migrated skips without inserting`() = runTest {
        settingsRepository.set(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, true)
        settingsRepository.set(
            SettingsKeys.FAVORITE_DEVICE_HISTORY,
            legacyJson(legacyItem("AA:BB", "Watch A"))
        )
        awaitDiskReconciled(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, SettingsKeys.FAVORITE_DEVICE_HISTORY)

        repository.migrateLegacyFavoritesIfNeeded()

        assertThat(dao.getAll().first()).isEmpty()
    }

    @Test
    fun `no legacy data migrates as empty and sets flag`() = runTest {
        repository.migrateLegacyFavoritesIfNeeded()

        assertThat(settingsRepository.get(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM)).isTrue()
        assertThat(dao.getAll().first()).isEmpty()
    }
}
