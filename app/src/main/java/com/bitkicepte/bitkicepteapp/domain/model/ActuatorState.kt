package com.bitkicepte.bitkicepteapp.domain.model

import com.bitkicepte.bitkicepteapp.data.local.entity.ControlMode

data class ActuatorState(
    val fanDuty:  Int = 0,           // 0-100 PWM %
    val ledDuty:  Int = 0,           // 0-100 PWM %
    val pumpDuty: Int = 0,           // 0-100 PWM %
    val controlMode: ControlMode = ControlMode.MANUAL
) {
    val fanOn:  Boolean get() = fanDuty  > 0
    val ledOn:  Boolean get() = ledDuty  > 0
    val pumpOn: Boolean get() = pumpDuty > 0
}
