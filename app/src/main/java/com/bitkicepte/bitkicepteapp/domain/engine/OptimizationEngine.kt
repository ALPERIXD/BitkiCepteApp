package com.bitkicepte.bitkicepteapp.domain.engine

import com.bitkicepte.bitkicepteapp.domain.model.ActuatorState
import com.bitkicepte.bitkicepteapp.domain.model.SensorData
import com.bitkicepte.bitkicepteapp.data.local.entity.ControlMode
import com.bitkicepte.bitkicepteapp.data.local.entity.PlantProfile

/**
 * Karar motoru — saf Kotlin, Android bağımlılığı yok, test edilebilir.
 *
 * Akıllı Mod algoritmaları:
 *   1. Predictive Ventilation: Dışarısı serinleyecekse fan bekleme
 *   2. DLI Lighting          : Günlük ışık integrali eksiğini LED ile kapat
 *   3. VPD Irrigation        : Transpirasyon ihtiyacına göre sulama (PWM)
 *   4. VPD Fan Control       : Fanı VPD aralığına sokmak için kullan
 *   5. Anti-pattern          : Çakışan komutları engelle
 *
 * Not: Isıtıcı donanımdan çıkarıldı. Fan soğutma/havalandırma için kullanılıyor.
 * Otomatik Mod: basit eşik tabanlı — bitki profili parametrelerini kullanır.
 */
object OptimizationEngine {

    // Fallback sabitler (profil seçilmemişse)
    private const val FALLBACK_TEMP_MAX   = 28f
    private const val FALLBACK_SOIL_MIN   = 35f
    private const val FALLBACK_TARGET_DLI = 15f
    private const val FALLBACK_VPD_MIN    = 0.8f
    private const val FALLBACK_VPD_MAX    = 1.5f
    private const val HUM_MAX_HARD        = 85f  // nem bu değeri aşarsa her zaman fan

    data class Result(val state: ActuatorState, val reasons: List<String>)

    // ── AKILLI MOD ────────────────────────────────────────────────────────

    fun decide(
        sensor: SensorData,
        forecastTemp3h: Float? = null,
        dliAccumulated: Float = 0f,
        profile: PlantProfile? = null
    ): Result {
        val reasons = mutableListOf<String>()
        var fan = 0; var led = 0; var pump = 0

        val tempMax   = profile?.tempMaxC       ?: FALLBACK_TEMP_MAX
        val soilMin   = profile?.soilMinPercent ?: FALLBACK_SOIL_MIN
        val targetDli = profile?.targetDli      ?: FALLBACK_TARGET_DLI
        val vpdMin    = profile?.vpdMin         ?: FALLBACK_VPD_MIN
        val vpdMax    = profile?.vpdMax         ?: FALLBACK_VPD_MAX
        val vpd       = sensor.vpd

        // 1. PREDICTIVE VENTILATION
        val coolingComing = forecastTemp3h != null && forecastTemp3h < sensor.temperatureC - 3f
        if (sensor.temperatureC > tempMax) {
            fan = if (coolingComing) {
                reasons += "PREDICTIVE_VENT: Dış sıcaklık düşüyor (${forecastTemp3h}°C) → Fan %30"
                30
            } else {
                reasons += "VENTILATION: ${sensor.temperatureC.f1()}°C > ${tempMax.f1()}°C → Fan %75"
                75
            }
        }

        // 2. DLI LIGHTING
        val remaining = (targetDli - dliAccumulated).coerceAtLeast(0f)
        if (remaining > 0f && sensor.luxValue < 500f) {
            led = (remaining / targetDli * 100f).toInt().coerceIn(10, 100)
            reasons += "DLI_LIGHTING: Kalan ${remaining.f1()} mol/m² → LED %$led"
        } else if (sensor.luxValue >= 500f && remaining > 0f) {
            reasons += "DLI_LIGHTING: Güneş yeterli (${sensor.luxValue.toInt()} lux), LED kapalı"
        } else if (remaining <= 0f) {
            reasons += "DLI_LIGHTING: Günlük DLI hedefi karşılandı, LED kapalı"
        }

        // 3. VPD IRRIGATION — pompa PWM ile orantılı sulama
        when {
            vpd < vpdMin -> {
                // Küf riski — sulama yok
                reasons += "VPD_IRRIGATION: VPD=${vpd.f2()} kPa düşük, küf riski → sulama yok"
            }
            vpd > vpdMax && sensor.soilMoisturePercent < soilMin -> {
                // Yüksek transpirasyon + kuru toprak → tam sulama
                pump = 80
                reasons += "VPD_IRRIGATION: VPD=${vpd.f2()} kPa yüksek, toprak %${sensor.soilMoisturePercent.toInt()} → Pompa %80"
            }
            sensor.soilMoisturePercent < soilMin && vpd in vpdMin..vpdMax -> {
                // VPD ideal aralıkta ama toprak kuru → orta sulama
                pump = 50
                reasons += "VPD_IRRIGATION: VPD ideal, toprak kuru %${sensor.soilMoisturePercent.toInt()} → Pompa %50"
            }
        }

        // 4. VPD FAN CONTROL
        if (fan == 0) {
            when {
                vpd < vpdMin && sensor.humidityPercent > HUM_MAX_HARD -> {
                    fan = 50
                    reasons += "VPD_FAN: Nem %${sensor.humidityPercent.toInt()} yüksek → Fan %50"
                }
                vpd > vpdMax && sensor.temperatureC <= tempMax -> {
                    fan = 40
                    reasons += "VPD_FAN: VPD=${vpd.f2()} kPa yüksek → Fan %40"
                }
            }
        }

        // 5. ANTI-PATTERN — güneşte LED engeli
        if (led > 0 && sensor.luxValue > 500f) {
            led = 0
            reasons += "ANTI_PATTERN: Güneş yeterli → LED kapatıldı"
        }

        return Result(
            state = ActuatorState(fan, led, pump, ControlMode.SMART),
            reasons = reasons.ifEmpty { listOf("Tüm değerler normal, enerji tasarrufu aktif") }
        )
    }

    // ── OTOMATİK MOD ─────────────────────────────────────────────────────

    fun autoDecide(
        sensor: SensorData,
        profile: PlantProfile? = null
    ): Result {
        val reasons = mutableListOf<String>()
        var fan = 0; var led = 0; var pump = 0

        val tempMax = profile?.tempMaxC       ?: FALLBACK_TEMP_MAX
        val soilMin = profile?.soilMinPercent ?: FALLBACK_SOIL_MIN

        // Havalandırma
        if (sensor.temperatureC > tempMax) {
            fan = 75
            reasons += "AUTO: ${sensor.temperatureC.f1()}°C > ${tempMax.f1()}°C → Fan %75"
        } else if (sensor.humidityPercent > HUM_MAX_HARD) {
            fan = 50
            reasons += "AUTO: Nem %${sensor.humidityPercent.toInt()} yüksek → Fan %50"
        }

        // Sulama (PWM)
        if (sensor.soilMoisturePercent < soilMin) {
            pump = 75
            reasons += "AUTO: Toprak %${sensor.soilMoisturePercent.toInt()} < %${soilMin.toInt()} → Pompa %75"
        }

        // Aydınlatma
        if (sensor.luxValue < 200f) {
            led = 60
            reasons += "AUTO: Işık ${sensor.luxValue.toInt()} lux → LED %60"
        }

        // Anti-pattern
        if (led > 0 && sensor.luxValue > 500f) {
            led = 0
            reasons += "AUTO: Güneş yeterli → LED kapatıldı"
        }

        return Result(
            state = ActuatorState(fan, led, pump, ControlMode.AUTO),
            reasons = reasons.ifEmpty { listOf("AUTO: Tüm değerler normal") }
        )
    }

    private fun Float.f1() = "%.1f".format(this)
    private fun Float.f2() = "%.2f".format(this)
}
