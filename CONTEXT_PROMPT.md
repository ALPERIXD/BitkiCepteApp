# BitkiCepte — Devam Prompt'u

## Proje
CodeXEnergy 2026 Hackathonu (Düzce Üniversitesi, 2-3 Mayıs 2026, 36 saat).
Tema: Akıllı Enerji Verimliliği. Proje: ESP32 tabanlı IoT sera otomasyon sistemi + Android uygulaması.

**Proje dizini:** `C:\Users\ALPER\AndroidStudioProjects\BitkiCepteApp`
**Package:** `com.bitkicepte.bitkicepteapp`
**minSdk:** 24, **targetSdk/compileSdk:** 36, **AGP:** 9.1.1

---

## Stack
- **Android:** Kotlin, MVVM + Clean Architecture, Room (KSP), Coroutines + Flow, MPAndroidChart, Material3 (MaterialComponents), ViewBinding, Navigation Component
- **KURAL:** Compose YOK. XML + ViewBinding. findViewById YOK. RxJava YOK.
- **ESP32:** TCP Socket + JSON, aynı WiFi ağında, bulut YOK

---

## Mevcut Dosyalar (TAMAMLANDI)

### Domain / Data katmanı
- `domain/model/SensorData.kt` — temperatureC, humidityPercent, soilMoisturePercent, luxValue, solarVoltage, solarCurrentA, solarPowerW, vpd (computed)
- `domain/model/ActuatorState.kt` — heaterDuty/fanDuty/ledDuty/pumpDuty (0-100), controlMode, heaterOn/fanOn/ledOn/pumpOn (computed)
- `data/local/entity/Enums.kt` — `enum class ControlMode { MANUAL, AUTO, SMART }`, `enum class ActuatorType { HEATER, FAN, LED, PUMP }`
- `data/local/entity/SensorReading.kt` — Room @Entity, tablo: sensor_readings
- `data/local/entity/ActuatorEvent.kt` — Room @Entity, tablo: actuator_events (actuatorType: ActuatorType enum)
- `data/local/entity/EnergyReading.kt` — Room @Entity, tablo: energy_readings (heaterWh, fanWh, ledWh, pumpWh, solarProductionWh)
- `data/local/entity/EnergyDailySummary.kt` — Room @Entity, tablo: energy_daily_summary
- `data/local/dao/SensorReadingDao.kt` — insert, getLatest(Flow), getSince, get15MinBuckets, get6HourBuckets, deleteOlderThan
- `data/local/dao/ActuatorEventDao.kt` — insert, getLatest, deleteOlderThan
- `data/local/dao/EnergyDao.kt` — insertReading, getTodayConsumptionWh(Flow), getTodaySolarWh(Flow), getLast7Days(Flow), getTodayBreakdown(suspend), deleteOlderThan. Ayrıca `EnergyBreakdown` data class burada.
- `data/local/database/AppDatabase.kt` — Singleton, version=1, @TypeConverters(EnumConverters::class) ile ControlMode/ActuatorType string olarak saklanıyor
- `data/network/Esp32Packet.kt` — `parse(json): SensorData?`, `buildCommand(state): String`. org.json.JSONObject kullanıyor (Gson YOK burada)
- `data/network/TcpSocketService.kt` — connect/disconnect/dataStream(Flow)/sendCommand. TCP, Dispatchers.IO
- `data/repository/GreenhouseRepository.kt` — TcpSocketService + 3 DAO birleştiriyor. StateFlow: connected, latestSensor, actuatorState. Her sensör paketinde EnergyEstimator ile EnergyReading kaydediyor.

### Domain engine'ler
- `domain/engine/EnergyEstimator.kt` — karakterizasyon tablosu: heater(0→50W), fan(0→6W), led(0→18W), pump(8W). heaterWatts(duty)/fanWatts/ledWatts/pumpWatts(Boolean)/toWh(watts,sec)
- `domain/engine/CarbonCalculator.kt` — GRID_KG_PER_KWH=0.42 (TEİAŞ 2024), PRICE_TL_PER_KWH=4.60, TREE_KG_PER_YEAR=21. consumptionToCo2/solarSavedCo2/co2ToTrees/kwhToCost/buildSummary
- `domain/engine/OptimizationEngine.kt` — `decide(sensor, forecastTemp3h?, dliAccumulated): Result`. 4 algoritma: PredictiveHeating, DLI Lighting, VPD Irrigation, Anti-pattern

### UI katmanı
- `BitkiCepteApp.kt` — Application class, AppDatabase başlatma, NotificationChannel
- `ui/MainActivity.kt` — NavHostFragment + BottomNavigationView.setupWithNavController, `val sharedViewModel by viewModels()`
- `ui/shared/SharedViewModel.kt` — activityViewModels, connect/disconnect/setMode/setActuator/onNewSensorData (smart mod tetikler), DLI birikimi, getTodayConsumptionWh/getTodaySolarWh/getLast7Days/getSensorSince/get15MinBuckets/get6HourBuckets/getTodayBreakdown delegate'leri
- `ui/home/HomeFragment.kt` — 5 sensör kartı (ItemSensorCardBinding), mod toggle (MANUAL/AUTO/SMART), 3 aktüatör satırı (ItemActuatorRowBinding: heater/fan/led slider+switch), pompa toggle, IP bağlantı dialog, smart mod reason text
- `ui/grafik/GrafikFragment.kt` — 1h/24h/7d toggle, 4 LineChart (TempHum, SoilLight, Solar, Consumption), MPAndroidChart
- `ui/enerji/EnerjiFragment.kt` — 3 KPI kart (consumption Wh, solar Wh, savings TL), BarChart haftalık, PieChart aktüatör dağılımı
- `ui/surdurulebilirlik/SurdurulebilirlikFragment.kt` — CO2 hero (tvCo2Value), ağaç eşdeğeri (tvTreeValue), maliyet (tvCostValue), solar kWh (tvSolarKwh), haftalık CO2 (tvWeeklyCo2)
- `ui/ayarlar/AyarlarFragment.kt` — SharedPreferences: esp32_ip, esp32_port, temp_min, temp_max, hum_max, soil_min, price_tl. Save + Connect butonu

### Res dosyaları
- `res/layout/activity_main.xml` — FragmentContainerView + BottomNavigationView
- `res/layout/fragment_home.xml`, `fragment_grafik.xml`, `fragment_enerji.xml`, `fragment_surdurulebilirlik.xml`, `fragment_ayarlar.xml`
- `res/layout/item_sensor_card.xml` — id'ler: tvSensorLabel, tvSensorValue, tvSensorUnit
- `res/layout/item_actuator_row.xml` — id'ler: tvActuatorLabel, tvActuatorDuty, switchActuator, seekActuator
- `res/layout/item_kpi_card.xml` — id'ler: tvKpiLabel, tvKpiValue, tvKpiUnit
- `res/menu/bottom_nav_menu.xml` — 5 item: nav_home, nav_grafik, nav_enerji, nav_surdurulebilirlik, nav_ayarlar
- `res/navigation/nav_graph.xml` — 5 fragment, startDestination: nav_home
- `res/drawable/` — ic_nav_home, ic_nav_chart, ic_nav_energy, ic_nav_eco, ic_nav_settings (vector)
- `res/values/colors.xml` — primary_green #2E7D32, primary_green_light #66BB6A, primary_green_dark #1B5E20, solar_yellow #F9A825, tech_blue #1976D2, bg_app #F1F8E9, bg_card #FFFFFF, text_primary #1A1A1A, text_secondary #757575, accent_red #E53935
- `res/values/strings.xml`, `res/values/themes.xml` (Theme.MaterialComponents.DayNight.NoActionBar)

### Gradle
```toml
# gradle/libs.versions.toml — önemli versiyonlar:
agp = "9.1.1"
ksp = "2.1.20-1.0.32"
material = "1.12.0"
navigation = "2.9.0"
room = "2.7.1"
lifecycle = "2.9.0"
mpandroidchart = "v3.1.0"   # JitPack'ten
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)  // AGP 9.1.1 — Kotlin zaten içinde, ayrıca ekleme!
    alias(libs.plugins.ksp)
}
// kotlinOptions YOK — AGP 9.x'te kaldırıldı
// compileOptions { sourceCompatibility/targetCompatibility = VERSION_11 }
// buildFeatures { viewBinding = true }
```

---

## Bilinen Hatalar / Kurallar

1. **`kotlin-android` plugin EKLEME** — AGP 9.1.1 zaten içeriyor, ekleyince "extension already registered" hatası verir
2. **`kotlinOptions` YOK** — AGP 9.x'te kaldırıldı, `compileOptions` içinde sadece sourceCompatibility/targetCompatibility
3. **Room enum converter ŞART** — `AppDatabase`'de `EnumConverters` class'ı ve `@TypeConverters` annotation'ı var
4. **`@color/primary` YOK** — `@color/primary_green` kullan
5. **`settings.gradle.kts`'de JitPack repo var** — MPAndroidChart için gerekli
6. **`ui/MainActivity.kt` doğru olan** — root package'daki `MainActivity.kt` içi boş (sadece package declaration)

---

## YAPILACAKLAR (Öncelik sırasıyla)

### 1. Derleme hatalarını bitir (DEVAM EDİYOR)
Muhtemelen başka derleme hataları çıkacak. Hataları tek tek düzelt.

### 2. Demo için gerçekçi veri akışı test et
- ESP32 bağlı değilken uygulama açılmalı (crash olmamalı)
- AyarlarFragment'ten IP gir → Bağlan → HomeFragment'te sensör kartları dolmalı

### 3. WiFiTcpForegroundService (opsiyonel ama güzel)
- Uygulama arka plana geçince TCP bağlantısı kopmaması için ForegroundService
- `AndroidManifest.xml`'e `<service android:name=".data.network.WiFiTcpForegroundService" android:foregroundServiceType="dataSync" />`

### 4. Demo polish (hackathon jürisi için)
- HomeFragment: bağlı değilken sensör kartları "--" göstermeli (şu an gösteriyor, kontrol et)
- Akıllı mod seçilince `tvSmartReason` görünmeli, algoritma kararını yazmalı
- EnerjiFragment'te BarChart X ekseni gün isimleri göstermeli
- SurdurulebilirlikFragment'te değerler çok küçük (0.000x) — demo için ESP32'den gerçekçi veri lazım

### 5. ESP32 firmware (Muhammed / Cihad)
JSON protokolü:
- ESP32 → Android: `{"t":22.5,"h":65.0,"s":42.0,"l":1200.0,"sv":18.2,"si":0.85,"sp":15.47,"ts":1714567890}`
- Android → ESP32: `{"heater":50,"fan":0,"led":75,"pump":0,"mode":"SMART"}`
- TCP server, port 8080, her ~2 saniyede bir gönder

---

## Mimari Özeti
```
ESP32 (TCP:8080)
    ↓ JSON
TcpSocketService.dataStream() : Flow<SensorData>
    ↓
GreenhouseRepository
    ├── SensorReadingDao (Room)
    ├── EnergyDao (Room) ← EnergyEstimator ile Wh hesabı
    └── ActuatorEventDao (Room)
    ↓ StateFlow
SharedViewModel (activityViewModels)
    ├── OptimizationEngine (SMART mod)
    └── 5 Fragment (HomeFragment, GrafikFragment, EnerjiFragment, SurdurulebilirlikFragment, AyarlarFragment)
```

---

## Önemli Kurallar
- **Türkçe** konuş
- **Compose KULLANMA**
- **Mock data KOYMA** — her sayı gerçek ölçüm veya "projeksiyon" olarak işaretle
- **Buzzword kullanma** (AI-powered, revolutionary vb.)
- Kısa ve çözüm odaklı cevap ver
- Sadece değişmesi gereken kodu göster (diff/snippet)
