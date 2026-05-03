/**
 * BitkiCepte — ESP32 Firmware (WiFi TCP + JSON)
 * Arduino Core v3.x uyumlu — v3 (Tüm aktüatörler MOSFET PWM)
 * 
 * Fan, LED, Su Pompası → IRLZ44N MOSFET → PWM
 */

#include <WiFi.h>
#include <Wire.h>
#include <DHT.h>
#include <Adafruit_INA219.h>
#include <ESP32Servo.h>
#include <time.h>

//#define BH1750_MEVCUT
#ifdef BH1750_MEVCUT
  #include <BH1750.h>
  BH1750 bh1750;
  bool bh1750Aktif = false;
#endif

// ═══════════════════════════════════════════════════════
// KONFİGÜRASYON
// ═══════════════════════════════════════════════════════
const char* WIFI_SSID = "Redmi Note11SE 5G";
const char* WIFI_PASS = "0123456789";
const int   TCP_PORT  = 8080;

const char* NTP_SERVER = "pool.ntp.org";
const long  GMT_OFFSET = 10800;

// ═══════════════════════════════════════════════════════
// PIN TANIMLARI
// ═══════════════════════════════════════════════════════
#define PIN_DHT         4
#define PIN_TOPRAK_NEM  36
#define PIN_IC_LDR      39

#define PIN_LDR_SOL_UST 35
#define PIN_LDR_SAG_UST 33
#define PIN_LDR_SOL_ALT 32
#define PIN_LDR_SAG_ALT 34

#define PIN_FAN   27
#define PIN_LED   25
#define PIN_POMPA 26

#define PIN_PAN  18
#define PIN_TILT 19

// PWM ayarları
#define PWM_FREQ       1000   // Fan + LED için (1kHz)
#define PWM_FREQ_POMPA  100   // Pompa için düşük frekans (motor dostu)
#define PWM_RES           8   // 8-bit → 0-255

// Minimum başlatma eşikleri (% cinsinden)
// Motor bu değerin altında dönemez, titreşir
#define FAN_MIN_DUTY   35
#define POMPA_MIN_DUTY 40

// ═══════════════════════════════════════════════════════
// NESNELER
// ═══════════════════════════════════════════════════════
DHT dht(PIN_DHT, DHT11);
Adafruit_INA219 ina219;
Servo panServo;
Servo tiltServo;

WiFiServer server(TCP_PORT);
WiFiClient client;

// ═══════════════════════════════════════════════════════
// DURUM
// ═══════════════════════════════════════════════════════
struct {
    int  fanDuty   = 0;   // 0-100
    int  ledDuty   = 0;   // 0-100
    int  pompaDuty = 0;   // 0-100 (artık kademeli!)
    char mode[10]  = "MANUAL";
} aktuatorDurum;

struct {
    int panAci  = 90;
    int tiltAci = 90;
} tracker;

const int TOLERANS = 150;
unsigned long sonGonderme   = 0;
unsigned long sonTrackerGun = 0;
const unsigned long SENSOR_ARALIK  = 2000;
const unsigned long TRACKER_ARALIK = 500;

String rxBuffer = "";

// ═══════════════════════════════════════════════════════
// FORWARD DECLARATIONS
// ═══════════════════════════════════════════════════════
void wifiBaglan();
void sensorVeriGonder();
void komutIsle(const String& json);
void aktuatorUygula();
void solarTrackerGuncelle();
bool jsonAnahtarVar(const String& json, const char* anahtar);
int  jsonInt(const String& json, const char* anahtar, int varsayilan);
String jsonStr(const String& json, const char* anahtar);
int dutyToPwm(int duty, int minDuty);

// ═══════════════════════════════════════════════════════
// SETUP
// ═══════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    Serial.println("\n=== BitkiCepte ESP32 v3 Baslıyor ===");

    dht.begin();
    Wire.begin();

    if (!ina219.begin()) {
        Serial.println("[WARN] INA219 bulunamadı!");
    }

#ifdef BH1750_MEVCUT
    bh1750Aktif = bh1750.begin(BH1750::CONTINUOUS_HIGH_RES_MODE);
    if (!bh1750Aktif) Serial.println("[INFO] BH1750 yok, LDR kullanılacak.");
#endif

    // Fan ve LED — 1kHz PWM
    ledcAttach(PIN_FAN, PWM_FREQ, PWM_RES);
    ledcAttach(PIN_LED, PWM_FREQ, PWM_RES);
    ledcWrite(PIN_FAN, 0);
    ledcWrite(PIN_LED, 0);

    // Pompa — 100Hz PWM (MOSFET, röle değil)
    ledcAttach(PIN_POMPA, PWM_FREQ_POMPA, PWM_RES);
    ledcWrite(PIN_POMPA, 0);

    // Servo
    panServo.setPeriodHertz(50);
    tiltServo.setPeriodHertz(50);
    panServo.attach(PIN_PAN, 500, 2400);
    tiltServo.attach(PIN_TILT, 500, 2400);
    panServo.write(tracker.panAci);
    tiltServo.write(tracker.tiltAci);

    wifiBaglan();
    configTime(GMT_OFFSET, 0, NTP_SERVER);
}

// ═══════════════════════════════════════════════════════
// LOOP
// ═══════════════════════════════════════════════════════
void loop() {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("[WiFi] Bağlantı koptu, yeniden bağlanıyor...");
        wifiBaglan();
        return;
    }

    if (!client || !client.connected()) {
        client = server.accept();
        if (client) {
            Serial.print("[TCP] Android bağlandı: ");
            Serial.println(client.remoteIP());
            rxBuffer = "";
        }
    }

    if (client && client.connected()) {
        // Non-blocking okuma
        while (client.available()) {
            char c = client.read();
            if (c == '\n') {
                rxBuffer.trim();
                if (rxBuffer.length() > 0) {
                    komutIsle(rxBuffer);
                }
                rxBuffer = "";
            } else {
                rxBuffer += c;
            }
        }

        unsigned long simdi = millis();
        if (simdi - sonGonderme >= SENSOR_ARALIK) {
            sonGonderme = simdi;
            sensorVeriGonder();
        }
    }

    unsigned long simdi = millis();
    if (simdi - sonTrackerGun >= TRACKER_ARALIK) {
        sonTrackerGun = simdi;
        solarTrackerGuncelle();
    }
}

// ═══════════════════════════════════════════════════════
// SENSÖR VERİSİ GÖNDER
// ═══════════════════════════════════════════════════════
void sensorVeriGonder() {
    float t = dht.readTemperature();
    float h = dht.readHumidity();
    if (isnan(t)) t = 0.0f;
    if (isnan(h)) h = 0.0f;

    int toprakHam = analogRead(PIN_TOPRAK_NEM);
    int toprak = constrain(map(toprakHam, 4095, 1000, 0, 100), 0, 100);

    float lux = 0.0f;
#ifdef BH1750_MEVCUT
    if (bh1750Aktif) {
        lux = bh1750.readLightLevel();
    } else {
        int ldrHam = analogRead(PIN_IC_LDR);
        lux = map(ldrHam, 0, 4095, 0, 10000);
    }
#else
    int ldrHam = analogRead(PIN_IC_LDR);
    lux = map(ldrHam, 0, 4095, 0, 10000);
#endif

    float sv = ina219.getBusVoltage_V();
    float si = ina219.getCurrent_mA() / 1000.0f;
    float sp = sv * si;

    time_t ts;
    time(&ts);

    // pompaDuty da JSON'a eklendi — Android bu değeri gösterebilir
    char buf[256];
    snprintf(buf, sizeof(buf),
        "{\"t\":%.1f,\"h\":%.1f,\"s\":%.1f,\"l\":%.1f,"
        "\"sv\":%.2f,\"si\":%.3f,\"sp\":%.2f,\"ts\":%lu,"
        "\"fan\":%d,\"led\":%d,\"pump\":%d,\"mode\":\"%s\"}\n",
        t, h, (float)toprak, lux, sv, si, sp, (unsigned long)ts,
        aktuatorDurum.fanDuty, aktuatorDurum.ledDuty,
        aktuatorDurum.pompaDuty, aktuatorDurum.mode);

    client.print(buf);
    Serial.print("[TX] "); Serial.print(buf);
}

// ═══════════════════════════════════════════════════════
// KOMUT PARSE
// Gelen JSON: {"fan":75,"led":100,"pump":60,"mode":"MANUAL"}
// Eksik anahtar → mevcut değeri koru
// ═══════════════════════════════════════════════════════
void komutIsle(const String& json) {
    Serial.print("[RX] "); Serial.println(json);

    if (jsonAnahtarVar(json, "\"fan\":")) {
        aktuatorDurum.fanDuty = constrain(jsonInt(json, "\"fan\":", 0), 0, 100);
    }
    if (jsonAnahtarVar(json, "\"led\":")) {
        aktuatorDurum.ledDuty = constrain(jsonInt(json, "\"led\":", 0), 0, 100);
    }
    if (jsonAnahtarVar(json, "\"pump\":")) {
        aktuatorDurum.pompaDuty = constrain(jsonInt(json, "\"pump\":", 0), 0, 100);
    }

    String mod = jsonStr(json, "\"mode\":\"");
    if (mod.length() > 0) mod.toCharArray(aktuatorDurum.mode, 10);

    aktuatorUygula();
}

// ═══════════════════════════════════════════════════════
// AKTÜATÖR UYGULA
// ═══════════════════════════════════════════════════════
void aktuatorUygula() {
    int ledPwm   = map(constrain(aktuatorDurum.ledDuty, 0, 100), 0, 100, 0, 255);
    int fanPwm   = dutyToPwm(aktuatorDurum.fanDuty,   FAN_MIN_DUTY);
    int pompaPwm = dutyToPwm(aktuatorDurum.pompaDuty, POMPA_MIN_DUTY);

    ledcWrite(PIN_LED,   ledPwm);
    ledcWrite(PIN_FAN,   fanPwm);
    ledcWrite(PIN_POMPA, pompaPwm);

    Serial.printf("[ACT] fan=%d%%(pwm=%d) led=%d%%(pwm=%d) pompa=%d%%(pwm=%d) mod=%s\n",
        aktuatorDurum.fanDuty,   fanPwm,
        aktuatorDurum.ledDuty,   ledPwm,
        aktuatorDurum.pompaDuty, pompaPwm,
        aktuatorDurum.mode);
}

// ═══════════════════════════════════════════════════════
// PWM HESAPLAMA — minimum eşik ile
// duty=0  → PWM=0 (tamamen kapat)
// duty>0  → minDuty ile 100 arasına ölçekle, sonra 0-255'e çevir
// ═══════════════════════════════════════════════════════
int dutyToPwm(int duty, int minDuty) {
    if (duty <= 0) return 0;
    int scaled = map(constrain(duty, 1, 100), 1, 100, minDuty, 100);
    return map(scaled, 0, 100, 0, 255);
}

// ═══════════════════════════════════════════════════════
// SOLAR TRACKER
// ═══════════════════════════════════════════════════════
void solarTrackerGuncelle() {
    int su   = analogRead(PIN_LDR_SOL_UST);
    int sagu = analogRead(PIN_LDR_SAG_UST);
    int sa   = analogRead(PIN_LDR_SOL_ALT);
    int saga = analogRead(PIN_LDR_SAG_ALT);

    int ortUst = (su + sagu) / 2;
    int ortAlt = (sa + saga) / 2;
    int ortSol = (su + sa)   / 2;
    int ortSag = (sagu + saga) / 2;

    bool hareket = false;

    if (abs(ortUst - ortAlt) > TOLERANS) {
        tracker.tiltAci += (ortUst > ortAlt) ? -1 : 1;
        hareket = true;
    }
    if (abs(ortSol - ortSag) > TOLERANS) {
        tracker.panAci += (ortSol > ortSag) ? 1 : -1;
        hareket = true;
    }

    tracker.panAci  = constrain(tracker.panAci,  10, 170);
    tracker.tiltAci = constrain(tracker.tiltAci, 10, 170);

    if (hareket) {
        panServo.write(tracker.panAci);
        tiltServo.write(tracker.tiltAci);
    }
}

// ═══════════════════════════════════════════════════════
// JSON YARDIMCILARI
// ═══════════════════════════════════════════════════════
bool jsonAnahtarVar(const String& json, const char* anahtar) {
    return json.indexOf(anahtar) >= 0;
}

int jsonInt(const String& json, const char* anahtar, int varsayilan) {
    int idx = json.indexOf(anahtar);
    if (idx < 0) return varsayilan;
    return json.substring(idx + strlen(anahtar)).toInt();
}

String jsonStr(const String& json, const char* anahtar) {
    int start = json.indexOf(anahtar);
    if (start < 0) return "";
    start += strlen(anahtar);
    int end = json.indexOf("\"", start);
    return (end < 0) ? "" : json.substring(start, end);
}

// ═══════════════════════════════════════════════════════
// WiFi BAĞLANTI
// ═══════════════════════════════════════════════════════
void wifiBaglan() {
    Serial.printf("[WiFi] Baglanıyor: %s\n", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASS);

    int deneme = 40;
    while (WiFi.status() != WL_CONNECTED && deneme-- > 0) {
        delay(500);
        Serial.print(".");
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("\n[WiFi] Baglandi! IP: %s\n", WiFi.localIP().toString().c_str());
        Serial.printf("[TCP]  Port %d dinleniyor\n", TCP_PORT);
        Serial.println("[INFO] Bu IP'yi Android uygulamasina girin!");
        server.begin();
        server.setNoDelay(true);
    } else {
        Serial.println("\n[HATA] WiFi baglantisi basarisiz!");
    }
}