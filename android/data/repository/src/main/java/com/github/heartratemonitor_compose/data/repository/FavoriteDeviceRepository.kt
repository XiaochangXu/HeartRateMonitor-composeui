package com.github.heartratemonitor_compose.data.repository

import android.util.Log
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceDao
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceEntity
import com.github.heartratemonitor_compose.data.model.FavoriteDeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteDeviceRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dao: FavoriteDeviceDao
) {

    suspend fun migrateLegacyFavoritesIfNeeded() {
        if (settingsRepository.get(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM)) return
        val json = settingsRepository.getNullable(SettingsKeys.FAVORITE_DEVICE_HISTORY) ?: "[]"
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)
                    dao.insert(
                        FavoriteDeviceEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                } catch (e: Exception) {
                    // 单条数据损坏只跳过该条，不中断整批迁移
                    Log.w("FavoriteDeviceRepository", "收藏历史第 $i 条迁移失败，跳过", e)
                }
            }
        } catch (e: Exception) {
            // ⚠️ 反直觉设计：不置位完成标志，避免"迁移失败却标记已完成"导致旧数据永久丢失。
            Log.w("FavoriteDeviceRepository", "收藏历史 JSON 解析失败，下次启动重试迁移", e)
            return
        }
        settingsRepository.set(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, true)
    }

    fun getFavoriteDeviceId(): String? {
        return settingsRepository.getNullable(SettingsKeys.FAVORITE_DEVICE_ID)
    }

    fun setFavoriteDeviceId(id: String?) {
        if (id != null) {
            settingsRepository.set(SettingsKeys.FAVORITE_DEVICE_ID, id)
        } else {
            settingsRepository.remove(SettingsKeys.FAVORITE_DEVICE_ID)
        }
    }

    fun clearFavoriteDeviceId() {
        setFavoriteDeviceId(null)
    }

    suspend fun addFavoriteDevice(id: String, name: String) {
        dao.insert(
            FavoriteDeviceEntity(
                id = id,
                name = name,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteFavoriteDevice(id: String) {
        dao.deleteById(id)
    }

    // 删除并恢复最近收藏（无剩余时清空当前收藏 ID）。
    suspend fun deleteAndRestoreLatest(id: String) {
        dao.deleteById(id)
        val latest = dao.getLatest()
        if (latest != null) {
            setFavoriteDeviceId(latest.id)
        } else {
            clearFavoriteDeviceId()
        }
    }

    suspend fun getLatestFavoriteDevice(): FavoriteDeviceInfo? {
        return dao.getLatest()?.toInfo()
    }

    fun getAllFavorites(): Flow<List<FavoriteDeviceInfo>> =
        dao.getAll().map { devices -> devices.map { it.toInfo() } }

    suspend fun addFavorite(device: FavoriteDeviceInfo) {
        dao.insert(device.toEntity())
    }

    suspend fun removeFavorite(id: String) {
        dao.deleteById(id)
    }

    suspend fun clearAllFavorites() {
        dao.deleteAll()
    }

    suspend fun isFavorite(id: String): Boolean {
        return dao.existsById(id)
    }
}