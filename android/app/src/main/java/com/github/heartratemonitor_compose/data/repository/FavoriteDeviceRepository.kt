package com.github.heartratemonitor_compose.data.repository

import android.app.Application
import android.util.Log
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceEntity
import com.github.heartratemonitor_compose.data.di.appContainer
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * 收藏设备相关持久化操作封装。
 *
 * 将 SharedPreferences 读写与 Room 访问从 [MainViewModel] 下沉到 repository 层，
 * 同时完成旧版 JSON 收藏历史的一次性迁移。
 *
 * 通过 [AppContainer] 注入 [SettingsRepository] 与 [AppDatabase][com.github.heartratemonitor_compose.data.db.AppDatabase]，
 * 消除散布的 `AppDatabase.getDatabase(context)` 直接调用与 `context.getSharedPreferences()` 手动构造。
 */
class FavoriteDeviceRepository(private val application: Application) {

    private val settingsRepository = application.appContainer.settingsRepository
    private val dao = application.appContainer.appDatabase.favoriteDeviceDao()

    suspend fun migrateLegacyFavoritesIfNeeded() {
        if (settingsRepository.getBoolean(PrefsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, false)) return
        val json = settingsRepository.getStringNullable(PrefsKeys.FAVORITE_DEVICE_HISTORY) ?: "[]"
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                dao.insert(
                    FavoriteDeviceEntity(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("FavoriteDeviceRepository", "收藏历史迁移到 Room 失败", e)
        }
        settingsRepository.setBoolean(PrefsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, true)
    }

    fun getFavoriteDeviceId(): String? {
        return settingsRepository.getStringNullable(PrefsKeys.FAVORITE_DEVICE_ID)
    }

    fun setFavoriteDeviceId(id: String?) {
        if (id != null) {
            settingsRepository.setString(PrefsKeys.FAVORITE_DEVICE_ID, id)
        } else {
            settingsRepository.remove(PrefsKeys.FAVORITE_DEVICE_ID)
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

    suspend fun getLatestFavoriteDevice(): FavoriteDeviceEntity? {
        return dao.getLatest()
    }

    fun getAllFavorites(): Flow<List<FavoriteDeviceEntity>> = dao.getAll()

    suspend fun addFavorite(device: FavoriteDeviceEntity) {
        dao.insert(device)
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
