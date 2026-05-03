package com.bitkicepte.bitkicepteapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bitki profili — her türün biyolojik ihtiyaçlarını tanımlar.
 * Akıllı mod bu değerleri kullanarak karar verir.
 */
@Entity(tableName = "plant_profiles")
data class PlantProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,               // "Marul", "Domates", "Çilek"
    val targetDli: Float,           // mol/m²/gün
    val vpdMin: Float,              // kPa — ideal VPD alt sınırı
    val vpdMax: Float,              // kPa — ideal VPD üst sınırı
    val soilMinPercent: Float,      // % — sulama başlangıç eşiği
    val tempMinC: Float,            // °C — ısıtma eşiği
    val tempMaxC: Float,            // °C — havalandırma eşiği
    val isCustom: Boolean = false   // kullanıcı tanımlı mı?
)
