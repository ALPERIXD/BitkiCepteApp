package com.bitkicepte.bitkicepteapp.domain.model

import com.bitkicepte.bitkicepteapp.data.local.entity.ControlMode

data class ActuatorState(
    val fanDuty:  Int = 0,           // 0-100 PWM %
    val ledDuty:  Int = 0,           // 0-100 PWM %
    val pumpOn:   Boolean = false,   // Röle — ON/OFF
    val controlMode: ControlMode = ControlMode.MANUAL
) {
    val fanOn: Boolean get() = fanDuty > 0
    val ledOn: Boolean get() = ledDuty > 0
}
