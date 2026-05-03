package com.bitkicepte.bitkicepteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bitkicepte.bitkicepteapp.data.local.entity.ActuatorEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface ActuatorEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ActuatorEvent)

    @Query("SELECT * FROM actuator_events ORDER BY timestamp DESC LIMIT :limit")
    fun getLatest(limit: Int = 50): Flow<List<ActuatorEvent>>

    @Query("DELETE FROM actuator_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
