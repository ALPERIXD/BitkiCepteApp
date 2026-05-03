package com.bitkicepte.bitkicepteapp.domain.engine

/**
 * Karakterizasyon tablosu: PWM duty % → Watt
 * 12V 5A adaptör — fan ~6W, LED ~18W, pompa ~8W (artık PWM kontrollü)
 * Isıtıcı projeden çıkarıldı.
 */
object EnergyEstimator {

    private val fanTable  = mapOf(0 to 0f, 25 to 1.5f, 50 to 3f,  100 to 6f)
    private val ledTable  = mapOf(0 to 0f, 25 to 5f,   50 to 10f, 100 to 18f)
    private val pumpTable = mapOf(0 to 0f, 25 to 2f,   50 to 4.5f, 100 to 8f)

    fun fanWatts(duty: Int): Float  = interpolate(fanTable, duty)
    fun ledWatts(duty: Int): Float  = interpolate(ledTable, duty)
    fun pumpWatts(duty: Int): Float = interpolate(pumpTable, duty)

    /** Watt × süre(sn) → Wh */
    fun toWh(watts: Float, durationSec: Float): Float = watts * durationSec / 3600f

    private fun interpolate(table: Map<Int, Float>, duty: Int): Float {
        val d    = duty.coerceIn(0, 100)
        val keys = table.keys.sorted()
        val lo   = keys.lastOrNull  { it <= d } ?: return 0f
        val hi   = keys.firstOrNull { it >= d } ?: return table[lo] ?: 0f
        if (lo == hi) return table[lo] ?: 0f
        val ratio = (d - lo).toFloat() / (hi - lo).toFloat()
        return (table[lo] ?: 0f) + ratio * ((table[hi] ?: 0f) - (table[lo] ?: 0f))
    }
}
