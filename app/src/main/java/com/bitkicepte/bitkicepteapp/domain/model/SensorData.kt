package com.bitkicepte.bitkicepteapp.domain.model

data class SensorData(
    val timestamp: Long = System.currentTimeMillis(),
    val temperatureC: Float,
    val humidityPercent: Float,
    val soilMoisturePercent: Float,
    val luxValue: Float,
    val solarVoltage: Float = 0f,
    val solarCurrentA: Float = 0f,
    val solarPowerW: Float = 0f
) {
    /** Buhar basıncı açığı (kPa) — VPD sulama kararı için */
    val vpd: Float get() {
        val svp = 0.6108f * Math.exp(17.27 * temperatureC / (temperatureC + 237.3)).toFloat()
        return svp * (1f - humidityPercent / 100f)
    }
}
