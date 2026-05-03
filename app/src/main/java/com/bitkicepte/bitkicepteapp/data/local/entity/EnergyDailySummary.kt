package com.bitkicepte.bitkicepteapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "energy_daily_summary")
data class EnergyDailySummary(
    @PrimaryKey val dateEpochDay: Long,
    val totalConsumptionKwh: Float,
    val solarProductionKwh: Float,
    val netConsumptionKwh: Float,
    val savedCo2Kg: Float,
    val costTl: Float,
    val fanKwh: Float = 0f,
    val ledKwh: Float = 0f,
    val pumpKwh: Float = 0f
)
