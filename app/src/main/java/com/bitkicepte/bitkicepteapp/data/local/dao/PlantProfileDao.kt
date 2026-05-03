package com.bitkicepte.bitkicepteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bitkicepte.bitkicepteapp.data.local.entity.PlantProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantProfileDao {

    @Query("SELECT * FROM plant_profiles ORDER BY isCustom ASC, id ASC")
    fun getAll(): Flow<List<PlantProfile>>

    @Query("SELECT * FROM plant_profiles WHERE id = :id")
    suspend fun getById(id: Int): PlantProfile?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(profiles: List<PlantProfile>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: PlantProfile): Long

    @Query("DELETE FROM plant_profiles WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomById(id: Int)

    @Query("SELECT COUNT(*) FROM plant_profiles")
    suspend fun count(): Int
}
