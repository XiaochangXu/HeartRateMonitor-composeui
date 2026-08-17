package com.github.heartratemonitor_compose.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDeviceDao {
    @Query("SELECT * FROM favorite_devices ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FavoriteDeviceEntity>>

    @Query("SELECT * FROM favorite_devices ORDER BY timestamp DESC")
    suspend fun getAllRaw(): List<FavoriteDeviceEntity>

    /**
     * 高效查询最近一条收藏设备（LIMIT 1），避免全表加载后取首个。
     */
    @Query("SELECT * FROM favorite_devices ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): FavoriteDeviceEntity?

    /**
     * 高效判断指定 ID 是否已收藏（EXISTS 子查询），避免全表加载后遍历。
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_devices WHERE id = :id)")
    suspend fun existsById(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: FavoriteDeviceEntity)

    @Query("DELETE FROM favorite_devices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorite_devices")
    suspend fun deleteAll()
}
