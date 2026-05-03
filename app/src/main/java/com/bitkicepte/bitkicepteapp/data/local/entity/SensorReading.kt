package com.bitkicepte.bitkicepteapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val temperatureC: Float,
    val humidityPercent: Float,
    val soilMoisturePercent: Float,
    val luxValue: Float,
    val solarVoltage: Float = 0f,
    val solarCurrentA: Float = 0f,
    val solarPowerW: Float = 0f
)
