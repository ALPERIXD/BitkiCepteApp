package com.bitkicepte.bitkicepteapp.data.network

import com.bitkicepte.bitkicepteapp.domain.model.ActuatorState
import com.bitkicepte.bitkicepteapp.domain.model.SensorData
import org.json.JSONObject

/**
 * ESP32 ↔ Android JSON protokolü (v5 uyumlu).
 *
 * ESP32 → Android (her ~2 saniyede bir):
 * {"t":22.5,"h":65.0,"s":42.0,"lux":1200.0,
 *  "sv":18.2,"sa":0.085,"sw":1.55,
 *  "vpd":0.92,"dli":0.0034,
 *  "fan":75,"led":0,"pump":0,"mode":"SMART","fc":21.3}
 *
 * Android → ESP32 (aktüatör komutu):
 * {"fan":75,"led":100,"pump":75,"mode":"SMART"}
 *
 * Android → ESP32 (profil güncellemesi, isteğe bağlı):
 * {"tempMin":15,"tempMax":28,"vpdMin":0.8,"vpdMax":1.5,"soilMin":35,"targetDli":15}
 */
object Esp32Packet {

    data class ParseResult(
        val sensorData: SensorData,
        val forecastTemp3h: Float?   // ESP32'nin çektiği tahmin, null = pakette yok
    )

    fun parse(json: String): ParseResult? = try {
        val o = JSONObject(json)
        val sensor = SensorData(
            timestamp           = if (o.has("ts")) o.getLong("ts") * 1000L
                                  else System.currentTimeMillis(),
            temperatureC        = o.getDouble("t").toFloat(),
            humidityPercent     = o.getDouble("h").toFloat(),
            soilMoisturePercent = o.getDouble("s").toFloat(),
            // v5: "lux" (v4'te "l" idi — her ikisini de dene)
            luxValue            = o.optDouble("lux", o.optDouble("l", 0.0)).toFloat(),
            solarVoltage        = o.optDouble("sv", 0.0).toFloat(),
            // v5: "sa" (v4'te "si")
            solarCurrentA       = o.optDouble("sa", o.optDouble("si", 0.0)).toFloat(),
            // v5: "sw" (v4'te "sp")
            solarPowerW         = o.optDouble("sw", o.optDouble("sp", 0.0)).toFloat()
        )
        val fc = if (o.has("fc") && o.getDouble("fc") > -900.0)
            o.getDouble("fc").toFloat() else null
        ParseResult(sensor, fc)
    } catch (_: Exception) { null }

    fun buildCommand(state: ActuatorState): String = JSONObject().apply {
        put("fan",  state.fanDuty)
        put("led",  state.ledDuty)
        put("pump", state.pumpDuty)
        put("mode", state.controlMode.name)
    }.toString()

    /**
     * Seçili bitki profilini ESP32'ye gönder.
     * ESP32 bu değerleri kendi karar motorunda kullanır.
     */
    fun buildProfileCommand(
        tempMin: Float, tempMax: Float,
        vpdMin: Float,  vpdMax: Float,
        soilMin: Float, targetDli: Float
    ): String = JSONObject().apply {
        put("tempMin",   tempMin)
        put("tempMax",   tempMax)
        put("vpdMin",    vpdMin)
        put("vpdMax",    vpdMax)
        put("soilMin",   soilMin)
        put("targetDli", targetDli)
    }.toString()
}
