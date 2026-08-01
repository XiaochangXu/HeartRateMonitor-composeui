package com.github.heartratemonitor_compose.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏设备相关持久化操作封装。
 *
 * 将 SharedPreferences 读写与 Room 访问从 [MainViewModel] 下沉到 repository 层，
 * 同时完成旧版 JSON 收藏历史的一次性迁移。
 */
object FavoriteDeviceRepository {

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
    }

    private fun dao(context: Context) = AppDatabase.getDatabase(context.applicationContext).favoriteDeviceDao()

    suspend fun migrateLegacyFavoritesIfNeeded(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(PrefsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, false)) return
        val json = prefs.getString(PrefsKeys.FAVORITE_DEVICE_HISTORY, null) ?: "[]"
        try {
            val arr = JSONArray(json)
            val favoriteDao = dao(context)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                favoriteDao.insert(
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
        prefs.edit { putBoolean(PrefsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, true) }
    }

    fun getFavoriteDeviceId(context: Context): String? {
        return prefs(context).getString(PrefsKeys.FAVORITE_DEVICE_ID, null)
    }

    fun setFavoriteDeviceId(context: Context, id: String?) {
        prefs(context).edit { putString(PrefsKeys.FAVORITE_DEVICE_ID, id) }
    }

    fun clearFavoriteDeviceId(context: Context) {
        setFavoriteDeviceId(context, null)
    }

    suspend fun addFavoriteDevice(context: Context, id: String, name: String) {
        dao(context).insert(
            FavoriteDeviceEntity(
                id = id,
                name = name,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteFavoriteDevice(context: Context, id: String) {
        dao(context).deleteById(id)
    }

    suspend fun getLatestFavoriteDevice(context: Context): FavoriteDeviceEntity? {
        return dao(context).getAllRaw().firstOrNull()
    }

    fun getAllFavorites(context: Context): Flow<List<FavoriteDeviceEntity>> = dao(context).getAll()

    suspend fun addFavorite(context: Context, device: FavoriteDeviceEntity) {
        dao(context).insert(device)
    }

    suspend fun removeFavorite(context: Context, id: String) {
        dao(context).deleteById(id)
    }

    suspend fun clearAllFavorites(context: Context) {
        dao(context).deleteAll()
    }

    suspend fun isFavorite(context: Context, id: String): Boolean {
        return dao(context).getAllRaw().any { it.id == id }
    }
}
