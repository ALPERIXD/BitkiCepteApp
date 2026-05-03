/**
 * BitkiCepte — ESP32 Firmware v5
 *
 * Yenilikler (v4 → v5):
 *   - DHT22 (sıcaklık + nem)
 *   - BH1750 (lux)
 *   - INA219 (solar V/A/W)
 *   - NTP saat senkronizasyonu
 *   - OpenWeatherMap 3 saatlik tahmin (forecastTemp3h)
 *   - Otomatik Mod  : profil eşik tabanlı karar
 *   - Akıllı Mod    : Predictive Heating, DLI Lighting, VPD Irrigation,
 *                     VPD Fan Control, Anti-pattern, Dynamic Priority
 *   - TCP + JSON haberleşme (Android uygulama ile uyumlu)
 *   - Solar Tracker (çift eksen, LDR + astronomik)
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <DHT.h>
#include <Adafruit_INA219.h>
#include <ESP32Servo.h>
#include <BH1750.h>
#include <time.h>
#include <math.h>

// ═══════════════════════════════════════════════════════
// KONFİGÜRASYON
// ═══════════════════════════════════════════════════════
const char* WIFI_SSID   = "Redmi Note11SE 5G";
const char* WIFI_PASS   = "0123456789";
const int   TCP_PORT    = 8080;
const char* NTP_SERVER  = "pool.ntp.org";
const long  GMT_OFFSET  = 10800;  // UTC+3

// OpenWeatherMap — koordinatlar Düzce için (hackathon)
const char* OWM_KEY   = "b6f1e27939df55cf5a049d366e0b3fbf";
const float LOC_LAT   = 40.8438f;
const float LOC_LON   = 31.1565f;
// Tahmin her 30 dakikada bir güncellenir
#define FORECAST_INTERVAL_MS 1800000UL

// ═══════════════════════════════════════════════════════
// PIN TANIMLARI
// ═══════════════════════════════════════════════════════
#define PIN_DHT         4
#define PIN_TOPRAK_NEM  36
#define PIN_LDR_SOL_UST 35
#define PIN_LDR_SAG_UST 33
#define PIN_LDR_SOL_ALT 32
#define PIN_LDR_SAG_ALT 34
#define PIN_FAN         27
#define PIN_LED         25
#define PIN_POMPA       26
#define PIN_PAN         18
#define PIN_TILT        19

// ═══════════════════════════════════════════════════════
// PWM AYARLARI
// ═══════════════════════════════════════════════════════
#define PWM_RES      8
#define FREQ_GENEL   1000
#define FREQ_POMPA   150
#define FAN_MIN      90    // ~%35 — titreme eşiği
#define POMPA_MIN    110   // ~%43
#define LED_MIN      10

// ═══════════════════════════════════════════════════════
// NESNELER
// ═══════════════════════════════════════════════════════
DHT           dht(PIN_DHT, DHT22);
Adafruit_INA219 ina219;
BH1750        lightMeter;
Servo         panServo;
Servo         tiltServo;
WiFiServer    server(TCP_PORT);
WiFiClient    client;

// ═══════════════════════════════════════════════════════
// DURUM YAPILARI
// ═══════════════════════════════════════════════════════
struct Aktuator {
    int  fanDuty   = 0;
    int  ledDuty   = 0;
    int  pompaDuty = 0;
    char mode[10]  = "MANUAL";   // MANUAL | AUTO | SMART
} durum;

struct SensorVeri {
    float sicaklik   = 0;
    float nem        = 0;
    float toprakNem  = 0;
    float lux        = 0;
    float solarV     = 0;
    float solarA     = 0;
    float solarW     = 0;
} sensor;

// Bitki Profili (Android'den veya varsayılan)
struct BitkyProfil {
    float tempMinC       = 15.0f;
    float tempMaxC       = 28.0f;
    float vpdMin         = 0.8f;
    float vpdMax         = 1.5f;
    float soilMinPct     = 35.0f;
    float targetDli      = 15.0f;
} profil;

// Hava tahmini
float forecastTemp3h = -999.0f;   // -999 = henüz çekilmedi

// DLI birikimi (lux → mol/m²)
float dliAcc       = 0.0f;
unsigned long dliTs = 0;

// Zamanlayıcılar
unsigned long sonGonderme  = 0;
unsigned long sonTracker   = 0;
unsigned long sonForecast  = 0;

// Tracker
int panAci  = 90;
int tiltAci = 90;
const int TOLERANS = 150;

String rxBuffer = "";

// ═══════════════════════════════════════════════════════
// YARDIMCI: VPD hesabı
// ═══════════════════════════════════════════════════════
float hesaplaVpd(float tempC, float nemPct) {
    // Magnus formülü: doyma buhar basıncı (kPa)
    float svp = 0.6108f * exp(17.27f * tempC / (tempC + 237.3f));
    return svp * (1.0f - nemPct / 100.0f);
}

// ═══════════════════════════════════════════════════════
// YARDIMCI: duty → PWM
// ═══════════════════════════════════════════════════════
int dutyToPwm(int duty, int minPwm) {
    if (duty <= 0) return 0;
    return map(duty, 1, 100, minPwm, 255);
}

// ═══════════════════════════════════════════════════════
// AKTÜATÖR UYGULA
// ═══════════════════════════════════════════════════════
void aktuatorUygula() {
    ledcWrite(PIN_FAN,   dutyToPwm(durum.fanDuty,   FAN_MIN));
    ledcWrite(PIN_LED,   dutyToPwm(durum.ledDuty,   LED_MIN));
    ledcWrite(PIN_POMPA, dutyToPwm(durum.pompaDuty, POMPA_MIN));
    Serial.printf("[AKTUATOR] Fan:%%%d LED:%%%d Pompa:%%%d Mod:%s\n",
                  durum.fanDuty, durum.ledDuty, durum.pompaDuty, durum.mode);
}

// ═══════════════════════════════════════════════════════
// HAVA TAHMİNİ (OpenWeatherMap forecast/3h)
// ═══════════════════════════════════════════════════════
void forecastGuncelle() {
    if (WiFi.status() != WL_CONNECTED) return;

    HTTPClient http;
    char url[200];
    snprintf(url, sizeof(url),
        "http://api.openweathermap.org/data/2.5/forecast"
        "?lat=%.4f&lon=%.4f&cnt=2&units=metric&appid=%s",
        LOC_LAT, LOC_LON, OWM_KEY);

    http.begin(url);
    int code = http.GET();
    if (code == 200) {
        String body = http.getString();
        // "temp": değerini JSON'dan manuel çek (ArduinoJson olmadan)
        // İkinci list girişi (~3 saat sonrası) aranıyor
        int idx = body.indexOf("\"list\":[");
        if (idx >= 0) {
            // İkinci "temp": bul
            int t1 = body.indexOf("\"temp\":", idx);
            int t2 = (t1 >= 0) ? body.indexOf("\"temp\":", t1 + 7) : -1;
            int tIdx = (t2 >= 0) ? t2 : t1;
            if (tIdx >= 0) {
                float val = body.substring(tIdx + 7).toFloat();
                forecastTemp3h = val;
                Serial.printf("[FORECAST] 3h tahmin: %.1f°C\n", forecastTemp3h);
            }
        }
    } else {
        Serial.printf("[FORECAST] HTTP hata: %d\n", code);
    }
    http.end();
}

// ═══════════════════════════════════════════════════════
// AKILLI MOD — OptimizationEngine mantığının ESP32 karşılığı
// ═══════════════════════════════════════════════════════
void akillıModCalistir() {
    float vpd = hesaplaVpd(sensor.sicaklik, sensor.nem);

    int fan  = 0;
    int led  = 0;
    int pump = 0;

    // ── 6. DYNAMIC PRIORITY — Kritik Bitki Koruma ──
    bool nearCold   = sensor.sicaklik <= profil.tempMinC + 2.0f;
    bool forecastCold = (forecastTemp3h > -900.0f) &&
                        (forecastTemp3h <= profil.tempMinC + 2.0f);
    bool kritikKoruma = nearCold || forecastCold;

    if (kritikKoruma) {
        Serial.printf("[DYNAMIC_PRIORITY] Sicaklik kritik (%.1f°C) → Fan kapalı\n",
                      sensor.sicaklik);
    }

    // ── 1. PREDICTIVE VENTILATION ──
    bool sogumaGeliyor = (forecastTemp3h > -900.0f) &&
                         (forecastTemp3h < sensor.sicaklik - 3.0f);
    if (!kritikKoruma && sensor.sicaklik > profil.tempMaxC) {
        fan = sogumaGeliyor ? 30 : 75;
        Serial.printf("[VENT] %.1f°C > %.1f°C → Fan%%%d%s\n",
                      sensor.sicaklik, profil.tempMaxC, fan,
                      sogumaGeliyor ? " (predictive)" : "");
    }

    // ── 2. DLI LIGHTING ──
    float kalan = profil.targetDli - dliAcc;
    if (kalan < 0) kalan = 0;
    if (kalan > 0 && sensor.lux < 500.0f) {
        led = (int)(kalan / profil.targetDli * 100.0f);
        if (led < 10) led = 10;
        if (led > 100) led = 100;
        Serial.printf("[DLI] Kalan %.2f mol/m² → LED%%%d\n", kalan, led);
    } else if (sensor.lux >= 500.0f) {
        led = 0;
    }

    // ── 3. VPD IRRIGATION ──
    if (vpd < profil.vpdMin) {
        pump = 0;
        Serial.printf("[VPD_IRR] VPD=%.2f düşük, küf riski → sulama yok\n", vpd);
    } else if (vpd > profil.vpdMax && sensor.toprakNem < profil.soilMinPct) {
        pump = 80;
        Serial.printf("[VPD_IRR] VPD=%.2f yüksek + kuru → Pompa%%80\n", vpd);
    } else if (sensor.toprakNem < profil.soilMinPct && vpd >= profil.vpdMin && vpd <= profil.vpdMax) {
        pump = 50;
        Serial.printf("[VPD_IRR] VPD ideal, toprak kuru → Pompa%%50\n");
    }

    // ── 4. VPD FAN CONTROL ──
    if (fan == 0 && !kritikKoruma) {
        if (vpd < profil.vpdMin && sensor.nem > 85.0f) {
            fan = 50;
            Serial.printf("[VPD_FAN] Nem%%%.0f yüksek → Fan%%50\n", sensor.nem);
        } else if (vpd > profil.vpdMax && sensor.sicaklik <= profil.tempMaxC) {
            fan = 40;
            Serial.printf("[VPD_FAN] VPD=%.2f yüksek → Fan%%40\n", vpd);
        }
    }

    // ── 5. ANTI-PATTERN — güneşte LED engeli ──
    if (led > 0 && sensor.lux > 500.0f) {
        led = 0;
        Serial.println("[ANTI_PATTERN] Güneş yeterli → LED kapatıldı");
    }

    durum.fanDuty   = fan;
    durum.ledDuty   = led;
    durum.pompaDuty = pump;
    aktuatorUygula();
}

// ═══════════════════════════════════════════════════════
// OTOMATİK MOD — basit eşik tabanlı
// ═══════════════════════════════════════════════════════
void otomatikModCalistir() {
    int fan = 0, led = 0, pump = 0;

    if (sensor.sicaklik > profil.tempMaxC) {
        fan = 75;
    } else if (sensor.nem > 85.0f) {
        fan = 50;
    }

    if (sensor.toprakNem < profil.soilMinPct) {
        pump = 75;
    }

    if (sensor.lux < 200.0f) {
        led = 60;
    }

    // Anti-pattern
    if (led > 0 && sensor.lux > 500.0f) led = 0;

    durum.fanDuty   = fan;
    durum.ledDuty   = led;
    durum.pompaDuty = pump;
    aktuatorUygula();
}

// ═══════════════════════════════════════════════════════
// KOMUT İŞLE (Android'den gelen JSON)
// ═══════════════════════════════════════════════════════
void komutIsle(const String& json) {
    Serial.print("[RX] "); Serial.println(json);

    if (json.indexOf("\"fan\":") >= 0) {
        durum.fanDuty   = json.substring(json.indexOf("\"fan\":") + 6).toInt();
    }
    if (json.indexOf("\"led\":") >= 0) {
        durum.ledDuty   = json.substring(json.indexOf("\"led\":") + 6).toInt();
    }
    if (json.indexOf("\"pump\":") >= 0) {
        durum.pompaDuty = json.substring(json.indexOf("\"pump\":") + 7).toInt();
    }
    if (json.indexOf("\"mode\":\"") >= 0) {
        int s = json.indexOf("\"mode\":\"") + 8;
        int e = json.indexOf("\"", s);
        json.substring(s, e).toCharArray(durum.mode, 10);
    }

    // Profil güncellemesi (Android'den gelebilir)
    if (json.indexOf("\"tempMin\":") >= 0)
        profil.tempMinC   = json.substring(json.indexOf("\"tempMin\":") + 10).toFloat();
    if (json.indexOf("\"tempMax\":") >= 0)
        profil.tempMaxC   = json.substring(json.indexOf("\"tempMax\":") + 10).toFloat();
    if (json.indexOf("\"vpdMin\":") >= 0)
        profil.vpdMin     = json.substring(json.indexOf("\"vpdMin\":") + 9).toFloat();
    if (json.indexOf("\"vpdMax\":") >= 0)
        profil.vpdMax     = json.substring(json.indexOf("\"vpdMax\":") + 9).toFloat();
    if (json.indexOf("\"soilMin\":") >= 0)
        profil.soilMinPct = json.substring(json.indexOf("\"soilMin\":") + 10).toFloat();
    if (json.indexOf("\"targetDli\":") >= 0)
        profil.targetDli  = json.substring(json.indexOf("\"targetDli\":") + 12).toFloat();

    // Manuel modda aktüatörü hemen uygula
    // AUTO/SMART modda döngü zaten kararı verir
    if (strcmp(durum.mode, "MANUAL") == 0) {
        aktuatorUygula();
    }
}

// ═══════════════════════════════════════════════════════
// DLI BİRİKİMİ GÜNCELLE
// ═══════════════════════════════════════════════════════
void dliGuncelle() {
    unsigned long now = millis();
    float durSaat = (now - dliTs) / 3600000.0f;
    dliTs = now;
    // 1 lux ≈ 0.0185 µmol/m²/s → /1e6 × 3600 = 0.0185 × 3600/1e6 mol/m²/h
    dliAcc += sensor.lux * durSaat * 0.0185f * 3600.0f / 1e6f;

    // Gece yarısı sıfırla (NTP saatine göre)
    time_t now_t = time(nullptr);
    struct tm* t = localtime(&now_t);
    if (t->tm_hour == 0 && t->tm_min == 0 && t->tm_sec < 5) {
        dliAcc = 0.0f;
        Serial.println("[DLI] Günlük birikim sıfırlandı");
    }
}

// ═══════════════════════════════════════════════════════
// SENSÖR OKU
// ═══════════════════════════════════════════════════════
void sensorOku() {
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    if (!isnan(t)) sensor.sicaklik = t;
    if (!isnan(h)) sensor.nem      = h;

    int raw = analogRead(PIN_TOPRAK_NEM);
    sensor.toprakNem = constrain(map(raw, 4095, 1000, 0, 100), 0, 100);

    sensor.lux   = lightMeter.readLightLevel();
    sensor.solarV = ina219.getBusVoltage_V();
    sensor.solarA = ina219.getCurrent_mA() / 1000.0f;
    sensor.solarW = sensor.solarV * sensor.solarA;
    if (sensor.solarW < 0) sensor.solarW = 0;
}

// ═══════════════════════════════════════════════════════
// JSON GÖNDER (Android'e)
// ═══════════════════════════════════════════════════════
void veriGonder() {
    if (!client || !client.connected()) return;
    float vpd = hesaplaVpd(sensor.sicaklik, sensor.nem);
    client.printf(
        "{\"t\":%.1f,\"h\":%.1f,\"s\":%.0f,\"lux\":%.0f,"
        "\"sv\":%.2f,\"sa\":%.3f,\"sw\":%.2f,"
        "\"vpd\":%.3f,\"dli\":%.4f,"
        "\"fan\":%d,\"led\":%d,\"pump\":%d,"
        "\"mode\":\"%s\",\"fc\":%.1f}\n",
        sensor.sicaklik, sensor.nem, sensor.toprakNem, sensor.lux,
        sensor.solarV, sensor.solarA, sensor.solarW,
        vpd, dliAcc,
        durum.fanDuty, durum.ledDuty, durum.pompaDuty,
        durum.mode,
        forecastTemp3h
    );
}

// ═══════════════════════════════════════════════════════
// SOLAR TRACKER
// ═══════════════════════════════════════════════════════
void trackerGuncelle() {
    int solUst = analogRead(PIN_LDR_SOL_UST);
    int sagUst = analogRead(PIN_LDR_SAG_UST);
    int solAlt = analogRead(PIN_LDR_SOL_ALT);
    int sagAlt = analogRead(PIN_LDR_SAG_ALT);

    int yatay  = (solUst + solAlt) - (sagUst + sagAlt);
    int dikey  = (solUst + sagUst) - (solAlt + sagAlt);

    if (abs(yatay) > TOLERANS) {
        panAci += (yatay > 0) ? -1 : 1;
        panAci  = constrain(panAci, 0, 180);
        panServo.write(panAci);
    }
    if (abs(dikey) > TOLERANS) {
        tiltAci += (dikey > 0) ? 1 : -1;
        tiltAci  = constrain(tiltAci, 0, 90);
        tiltServo.write(tiltAci);
    }
}

// ═══════════════════════════════════════════════════════
// SETUP
// ═══════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);

    pinMode(PIN_FAN,   OUTPUT); digitalWrite(PIN_FAN,   LOW);
    pinMode(PIN_LED,   OUTPUT); digitalWrite(PIN_LED,   LOW);
    pinMode(PIN_POMPA, OUTPUT); digitalWrite(PIN_POMPA, LOW);

    ledcAttach(PIN_FAN,   FREQ_GENEL, PWM_RES);
    ledcAttach(PIN_LED,   FREQ_GENEL, PWM_RES);
    ledcAttach(PIN_POMPA, FREQ_POMPA, PWM_RES);

    dht.begin();
    Wire.begin();

    if (!ina219.begin())    Serial.println("[UYARI] INA219 bulunamadı!");
    if (!lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE))
                            Serial.println("[UYARI] BH1750 bulunamadı!");

    panServo.attach(PIN_PAN,  500, 2400);
    tiltServo.attach(PIN_TILT, 500, 2400);
    panServo.write(panAci);
    tiltServo.write(tiltAci);

    // WiFi
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    Serial.print("WiFi bağlanıyor");
    while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
    Serial.printf("\nIP: %s\n", WiFi.localIP().toString().c_str());

    // NTP
    configTime(GMT_OFFSET, 0, NTP_SERVER);
    Serial.println("NTP senkronize ediliyor...");
    struct tm ti;
    if (getLocalTime(&ti, 5000)) {
        Serial.printf("Saat: %02d:%02d:%02d\n", ti.tm_hour, ti.tm_min, ti.tm_sec);
    }

    // İlk hava tahmini
    forecastGuncelle();
    sonForecast = millis();

    dliTs = millis();
    server.begin();
    Serial.printf("TCP sunucu port %d hazır\n", TCP_PORT);
}

// ═══════════════════════════════════════════════════════
// LOOP
// ═══════════════════════════════════════════════════════
void loop() {
    // ── TCP bağlantı yönetimi ──
    if (!client || !client.connected()) {
        client = server.accept();
        if (client) {
            rxBuffer = "";
            Serial.println("[TCP] Android bağlandı");
        }
    }

    // ── Gelen komutları işle ──
    if (client && client.connected()) {
        while (client.available()) {
            char c = client.read();
            if (c == '\n') {
                rxBuffer.trim();
                if (rxBuffer.length() > 0) komutIsle(rxBuffer);
                rxBuffer = "";
            } else {
                rxBuffer += c;
            }
        }
    }

    // ── 2 saniyede bir sensör oku + karar ver + veri gönder ──
    if (millis() - sonGonderme >= 2000) {
        sonGonderme = millis();

        sensorOku();
        dliGuncelle();

        // Mod bazlı karar
        if (strcmp(durum.mode, "SMART") == 0) {
            akillıModCalistir();
        } else if (strcmp(durum.mode, "AUTO") == 0) {
            otomatikModCalistir();
        }
        // MANUAL: Android'den gelen duty değerleri zaten uygulandı

        veriGonder();
    }

    // ── 500ms: Solar Tracker ──
    if (millis() - sonTracker >= 500) {
        sonTracker = millis();
        trackerGuncelle();
    }

    // ── 30dk: Hava tahmini güncelle ──
    if (millis() - sonForecast >= FORECAST_INTERVAL_MS) {
        sonForecast = millis();
        forecastGuncelle();
    }
}
