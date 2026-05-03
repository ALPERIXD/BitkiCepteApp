package com.bitkicepte.bitkicepteapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "actuator_events")
data class ActuatorEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actuatorType: ActuatorType,
    val pwmDuty: Int,
    val isOn: Boolean,
    val controlMode: ControlMode,
    val triggerReason: String = ""
)
