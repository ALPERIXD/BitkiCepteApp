package com.bitkicepte.bitkicepteapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bitkicepte.bitkicepteapp.data.local.entity.SensorReading
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: SensorReading)

    /** Son N kayıt — anlık panel için */
    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getLatest(limit: Int = 120): Flow<List<SensorReading>>

    /** 1 saat: ham nokta (ESP32'den ~2sn bir gelir → max ~1800 nokta, biz 60 alıyoruz) */
    @Query("""
        SELECT * FROM sensor_readings
        WHERE timestamp > :since
        ORDER BY timestamp ASC
    """)
    suspend fun getSince(since: Long): List<SensorReading>

    /** 24 saat: 15 dakikalık ortalama bucket'lar */
    @Query("""
        SELECT 0 AS id,
               (timestamp / 900000 * 900000) AS timestamp,
               AVG(temperatureC) AS temperatureC,
               AVG(humidityPercent) AS humidityPercent,
               AVG(soilMoisturePercent) AS soilMoisturePercent,
               AVG(luxValue) AS luxValue,
               AVG(solarVoltage) AS solarVoltage,
               AVG(solarCurrentA) AS solarCurrentA,
               AVG(solarPowerW) AS solarPowerW
        FROM sensor_readings
        WHERE timestamp > :since
        GROUP BY (timestamp / 900000)
        ORDER BY timestamp ASC
    """)
    suspend fun get15MinBuckets(since: Long): List<SensorReading>

    /** 7 gün: 6 saatlik ortalama bucket'lar */
    @Query("""
        SELECT 0 AS id,
               (timestamp / 21600000 * 21600000) AS timestamp,
               AVG(temperatureC) AS temperatureC,
               AVG(humidityPercent) AS humidityPercent,
               AVG(soilMoisturePercent) AS soilMoisturePercent,
               AVG(luxValue) AS luxValue,
               AVG(solarVoltage) AS solarVoltage,
               AVG(solarCurrentA) AS solarCurrentA,
               AVG(solarPowerW) AS solarPowerW
        FROM sensor_readings
        WHERE timestamp > :since
        GROUP BY (timestamp / 21600000)
        ORDER BY timestamp ASC
    """)
    suspend fun get6HourBuckets(since: Long): List<SensorReading>

    @Query("DELETE FROM sensor_readings WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM sensor_readings")
    suspend fun deleteAll()
}
