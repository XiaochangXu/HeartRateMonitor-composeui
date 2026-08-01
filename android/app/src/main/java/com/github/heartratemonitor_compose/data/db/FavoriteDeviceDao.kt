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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: FavoriteDeviceEntity)

    @Query("DELETE FROM favorite_devices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorite_devices")
    suspend fun deleteAll()
}
