package com.bitkicepte.bitkicepteapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "energy_readings")
data class EnergyReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val fanWh: Float = 0f,
    val ledWh: Float = 0f,
    val pumpWh: Float = 0f,
    val solarProductionWh: Float = 0f
) {
    val totalConsumptionWh: Float get() = fanWh + ledWh + pumpWh
    val netConsumptionWh: Float   get() = (totalConsumptionWh - solarProductionWh).coerceAtLeast(0f)
}
