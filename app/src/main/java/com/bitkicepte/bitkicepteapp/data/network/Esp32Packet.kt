package com.bitkicepte.bitkicepteapp.data.network

import com.bitkicepte.bitkicepteapp.domain.model.ActuatorState
import com.bitkicepte.bitkicepteapp.domain.model.SensorData
import org.json.JSONObject

/**
 * ESP32 ↔ Android JSON protokolü.
 *
 * ESP32 → Android (her ~2 saniyede bir):
 * {"t":22.5,"h":65.0,"s":42.0,"l":1200.0,"sv":18.2,"si":0.85,"sp":15.47,"ts":1714567890}
 *
 * Android → ESP32 (komut):
 * {"fan":75,"led":100,"pump":1,"mode":"SMART"}
 */
object Esp32Packet {

    fun parse(json: String): SensorData? = try {
        val o = JSONObject(json)
        SensorData(
            timestamp           = if (o.has("ts")) o.getLong("ts") * 1000L else System.currentTimeMillis(),
            temperatureC        = o.getDouble("t").toFloat(),
            humidityPercent     = o.getDouble("h").toFloat(),
            soilMoisturePercent = o.getDouble("s").toFloat(),
            luxValue            = o.getDouble("l").toFloat(),
            solarVoltage        = o.optDouble("sv", 0.0).toFloat(),
            solarCurrentA       = o.optDouble("si", 0.0).toFloat(),
            solarPowerW         = o.optDouble("sp", 0.0).toFloat()
        )
    } catch (_: Exception) { null }

    fun buildCommand(state: ActuatorState): String = JSONObject().apply {
        put("fan",  state.fanDuty)
        put("led",  state.ledDuty)
        put("pump", if (state.pumpOn) 1 else 0)
        put("mode", state.controlMode.name)
    }.toString()
}
