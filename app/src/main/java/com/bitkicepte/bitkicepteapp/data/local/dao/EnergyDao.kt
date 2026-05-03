package com.bitkicepte.bitkicepteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bitkicepte.bitkicepteapp.data.local.entity.EnergyDailySummary
import com.bitkicepte.bitkicepteapp.data.local.entity.EnergyReading
import kotlinx.coroutines.flow.Flow

@Dao
interface EnergyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: EnergyReading)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySummary(summary: EnergyDailySummary)

    /** Bugünün toplam tüketimi (Wh) */
    @Query("""
        SELECT COALESCE(SUM(fanWh + ledWh + pumpWh), 0)
        FROM energy_readings WHERE timestamp >= :dayStartMs
    """)
    fun getTodayConsumptionWh(dayStartMs: Long): Flow<Float>

    /** Bugünün solar üretimi (Wh) */
    @Query("""
        SELECT COALESCE(SUM(solarProductionWh), 0)
        FROM energy_readings WHERE timestamp >= :dayStartMs
    """)
    fun getTodaySolarWh(dayStartMs: Long): Flow<Float>

    /** Son 7 günün günlük özetleri — haftalık bar chart için */
    @Query("""
        SELECT * FROM energy_daily_summary
        ORDER BY dateEpochDay DESC LIMIT 7
    """)
    fun getLast7Days(): Flow<List<EnergyDailySummary>>

    /** Aktüatör dağılımı — pie chart için (today) */
    @Query("""
        SELECT COALESCE(SUM(fanWh),0)  AS fanWh,
               COALESCE(SUM(ledWh),0)  AS ledWh,
               COALESCE(SUM(pumpWh),0) AS pumpWh
        FROM energy_readings WHERE timestamp >= :dayStartMs
    """)
    suspend fun getTodayBreakdown(dayStartMs: Long): EnergyBreakdown

    @Query("DELETE FROM energy_readings WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

data class EnergyBreakdown(
    val fanWh: Float,
    val ledWh: Float,
    val pumpWh: Float
)
