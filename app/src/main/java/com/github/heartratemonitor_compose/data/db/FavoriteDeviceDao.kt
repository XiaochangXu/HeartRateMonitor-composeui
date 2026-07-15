package com.github.heartratemonitor_compose.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDeviceDao {
    @Query("SELECT * FROM favorite_devices ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FavoriteDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: FavoriteDeviceEntity)

    @Query("DELETE FROM favorite_devices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorite_devices")
    suspend fun deleteAll()
}
