package com.bitkicepte.bitkicepteapp.domain.engine

/**
 * TEİAŞ 2024: 0.42 kg CO₂/kWh
 * EPDK 2024 konut tarifesi: ~4.60 ₺/kWh
 */
object CarbonCalculator {

    const val GRID_KG_PER_KWH  = 0.42f
    const val PRICE_TL_PER_KWH = 4.60f
    const val TREE_KG_PER_YEAR = 21f   // 1 ağaç ≈ 21 kg CO₂/yıl

    fun consumptionToCo2(kwh: Float): Float = kwh * GRID_KG_PER_KWH
    fun solarSavedCo2(solarKwh: Float): Float = solarKwh * GRID_KG_PER_KWH
    fun co2ToTrees(co2Kg: Float): Float = co2Kg / TREE_KG_PER_YEAR
    fun kwhToCost(kwh: Float): Float = kwh * PRICE_TL_PER_KWH

    data class Summary(
        val savedKwh: Float,
        val savedCo2Kg: Float,
        val savedTl: Float,
        val treeEquivalent: Float
    )

    fun buildSummary(savedKwh: Float) = Summary(
        savedKwh       = savedKwh,
        savedCo2Kg     = solarSavedCo2(savedKwh),
        savedTl        = kwhToCost(savedKwh),
        treeEquivalent = co2ToTrees(solarSavedCo2(savedKwh))
    )
}
