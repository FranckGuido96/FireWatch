// ===== CONNECTED FIRE PROTECTION SYSTEM: NodeMCU ESP8266 =====
// Local detection + sending measurements + receiving orders from the FireWatch website.
// Safety stays LOCAL: without Wi-Fi, automatic mode keeps working.
// Serial monitor: 115200 baud. Prints a status line every second plus events.
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClient.h>

// --- TO EDIT (never publish your Wi-Fi password on GitHub) ---
const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";
const char* SERVER_BASE = "http://192.168.X.X:3000";  // IP of the computer running Main.java
const char* API_KEY = "CHANGE_ME";                // same value as API_KEY on the server

#define MQ2_PIN A0
#define FLAME_PIN D5
#define BUZZER_PIN D6
#define LED_PIN D7
#define RELAY_PIN D1

#define SMOKE_WARMUP_MS 120000UL
#define FLAME_CONFIRM_MS 1000UL
#define PUMP_RUN_MS 10000UL
#define PUMP_COOLDOWN_MS 5000UL
#define BEEP_TIME 200
#define CMD_VALID_MS 6000UL        // website order is ignored if the server stops answering
#define STATUS_PERIOD_MS 1000UL    // serial status line period
#define WIFI_RETRY_MS 10000UL      // delay between Wi-Fi reconnection attempts

// Orders coming from the website (defaults = safest mode)
int smokeThreshold = 300;
bool autoMode = true;
bool remotePump = false;
bool buzzerMuted = false;
unsigned long lastCmdOk = 0;
unsigned long lastNet = 0;
unsigned long lastWifiRetry = 0;

unsigned long lastBeep = 0, pumpStart = 0, pumpStop = 0, flameSince = 0;
bool buzzerState = false, pumpRunning = false, pumpEverRan = false, relayState = false;

// Network status (for the serial monitor)
bool serverOk = false;
int lastPostCode = 0, lastGetCode = 0;

// Relay is active LOW: D1 LOW = pump ON, D1 floating (INPUT) = pump OFF
void pumpOn()  { pinMode(RELAY_PIN, OUTPUT); digitalWrite(RELAY_PIN, LOW); }
void pumpOff() { pinMode(RELAY_PIN, INPUT); }

void wifiConnect() {
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("[WIFI] Connecting to ");
  Serial.print(WIFI_SSID);
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 10000) {
    delay(250);
    Serial.print(".");
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print(" OK, IP ");
    Serial.print(WiFi.localIP());
    Serial.print(", signal ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  } else {
    Serial.println(" FAILED (the system keeps working without network)");
  }
}

// Every 2 s: send the measurement, then read the orders "pump mute threshold auto"
// Every 2 s (6 s if the server is down): send the measurement, then read the orders
void network(int smoke, bool flame, bool smokeAlarm, bool pump) {
  if (millis() - lastNet < (serverOk ? 2000UL : 6000UL)) return;   // CORRECTION 1 : moins d'essais si le serveur est KO
  lastNet = millis();

  if (WiFi.status() != WL_CONNECTED) {
    if (serverOk) Serial.println("[NET] Wi-Fi lost");
    serverOk = false;
    if (millis() - lastWifiRetry >= WIFI_RETRY_MS) {
      lastWifiRetry = millis();
      Serial.println("[WIFI] Reconnecting...");
      WiFi.reconnect();
    }
    return;
  }

  WiFiClient client;
  HTTPClient http;
  http.setTimeout(800);

  // 1) Send the measurement
  http.begin(client, String(SERVER_BASE) + "/api/mesures");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("x-api-key", API_KEY);
  String json = "{\"mq2\":" + String(smoke) + ",\"flamme\":" + (flame ? "true" : "false") +
                ",\"fumee\":" + (smokeAlarm ? "true" : "false") + ",\"pompe\":" + (pump ? "true" : "false") + "}";
  lastPostCode = http.POST(json);
  http.end();

  // 2) Read the orders (only if the server answered)
  lastGetCode = 0;
  if (lastPostCode > 0) {                                           // CORRECTION 2 : pas de GET si le serveur est injoignable
    http.begin(client, String(SERVER_BASE) + "/api/commande");
    http.addHeader("x-api-key", API_KEY);
    lastGetCode = http.GET();
    if (lastGetCode == 200) {
      int p, m, s, a;
      String answer = http.getString();
      if (sscanf(answer.c_str(), "%d %d %d %d", &p, &m, &s, &a) == 4) {
        bool newPump = (p == 1);
        bool newMute = (m == 1);
        int newThreshold = constrain(s, 10, 900);                   // CORRECTION 3 : 10 au lieu de 100 (comme le site)
        bool newAuto = (a == 1);

        // Print only when an order changes
        if (newPump != remotePump)           Serial.println(newPump ? "[CMD] Manual pump: ON" : "[CMD] Manual pump: OFF");
        if (newMute != buzzerMuted)          Serial.println(newMute ? "[CMD] Buzzer: MUTED" : "[CMD] Buzzer: RESTORED");
        if (newThreshold != smokeThreshold)  { Serial.print("[CMD] Smoke threshold: "); Serial.println(newThreshold); }
        if (newAuto != autoMode)             Serial.println(newAuto ? "[CMD] Automatic mode: ARMED" : "[CMD] Automatic mode: DISARMED");

        remotePump = newPump;
        buzzerMuted = newMute;
        smokeThreshold = newThreshold;
        autoMode = newAuto;
        lastCmdOk = millis();
      }
    }
    http.end();
  }

  // Report server connection changes
  bool ok = (lastPostCode == 201 && lastGetCode == 200);
  if (ok != serverOk) {
    serverOk = ok;
    if (ok) {
      Serial.println("[NET] Server connected");
    } else {
      Serial.print("[NET] Server problem: POST=");
      Serial.print(lastPostCode);
      Serial.print(" GET=");
      Serial.print(lastGetCode);
      Serial.println(" (-1 = unreachable, 401 = wrong API key)");
    }
  }
}

// One full status line per second on the serial monitor
void printStatus(unsigned long now, int smoke, bool flame, bool smokeAlarm, bool alarm, bool manualPump) {
  static unsigned long lastPrint = 0;
  if (now - lastPrint < STATUS_PERIOD_MS) return;
  lastPrint = now;

  Serial.print("[");
  Serial.print(now / 1000);
  Serial.print("s] MQ2=");
  Serial.print(smoke);
  Serial.print("/");
  Serial.print(smokeThreshold);
  if (now < SMOKE_WARMUP_MS) {
    Serial.print(" (warm-up ");
    Serial.print((SMOKE_WARMUP_MS - now) / 1000);
    Serial.print("s)");
  }
  Serial.print(" | smoke=");
  Serial.print(smokeAlarm ? "ALARM" : "ok");
  Serial.print(" | flame=");
  Serial.print(flame ? "DETECTED" : "none");
  Serial.print(" | alarm=");
  Serial.print(alarm ? "YES" : "no");
  Serial.print(" | buzzer=");
  if (!alarm) Serial.print("off");
  else Serial.print(buzzerMuted ? "MUTED" : "BEEPING");
  Serial.print(" | pump=");
  if (!relayState) Serial.print("off");
  else Serial.print(manualPump ? "ON (manual)" : "ON (auto)");
  Serial.print(" | mode=");
  Serial.print(autoMode ? "ARMED" : "DISARMED");
  Serial.print(" | wifi=");
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print(WiFi.localIP());
    Serial.print(" ");
    Serial.print(WiFi.RSSI());
    Serial.print("dBm");
  } else {
    Serial.print("OFFLINE");
  }
    Serial.print(" | server=");
  Serial.print(serverOk ? "OK" : "no");
  Serial.print(" (POST=");
  Serial.print(lastPostCode);
  Serial.print(" GET=");
  Serial.print(lastGetCode);
  Serial.println(")");
}

void setup() {
  Serial.begin(115200);
  pumpOff();
  pinMode(FLAME_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);

  Serial.println();
  Serial.println("=== CONNECTED FIRE PROTECTION SYSTEM ===");
  Serial.print("[BOOT] Reset reason: ");
  Serial.println(ESP.getResetReason());   // "Power On" / "External System" repeating = power problem
  Serial.print("[BOOT] Server: ");
  Serial.println(SERVER_BASE);
  Serial.print("[BOOT] Smoke sensor warm-up: ");
  Serial.print(SMOKE_WARMUP_MS / 1000);
  Serial.println("s (smoke alarm disabled until then)");
  wifiConnect();
}

void loop() {
  unsigned long now = millis();
  int smokeValue = analogRead(MQ2_PIN);
  bool flameDetected = (digitalRead(FLAME_PIN) == LOW);
  bool smokeDetected = (now > SMOKE_WARMUP_MS) && (smokeValue > smokeThreshold);
  bool alarm = flameDetected || smokeDetected;

  // Safety: if the server stops answering, forget the manual pump order
  bool manualPump = remotePump && (now - lastCmdOk < CMD_VALID_MS);

  // --- Events printed only when something changes ---
  static bool prevFlame = false, prevSmoke = false, prevAlarm = false, warmupDone = false;
  if (!warmupDone && now >= SMOKE_WARMUP_MS) {
    warmupDone = true;
    Serial.println("[INFO] Smoke sensor warm-up finished, smoke alarm active");
  }
  if (flameDetected != prevFlame) {
    prevFlame = flameDetected;
    Serial.println(flameDetected ? "[ALERT] FLAME DETECTED" : "[INFO] Flame gone");
  }
  if (smokeDetected != prevSmoke) {
    prevSmoke = smokeDetected;
    if (smokeDetected) {
      Serial.print("[ALERT] SMOKE above threshold: ");
      Serial.print(smokeValue);
      Serial.print(" > ");
      Serial.println(smokeThreshold);
    } else {
      Serial.println("[INFO] Smoke back to normal");
    }
  }
  if (alarm != prevAlarm) {
    prevAlarm = alarm;
    Serial.println(alarm ? "[ALARM] ALARM ON (LED on, buzzer active unless muted)" : "[ALARM] Alarm cleared");
  }

  // --- LED + buzzer (the buzzer can be muted from the website) ---
  if (alarm) {
    digitalWrite(LED_PIN, HIGH);
    if (buzzerMuted) {
      digitalWrite(BUZZER_PIN, LOW);
    } else if (now - lastBeep >= BEEP_TIME) {
      buzzerState = !buzzerState;
      digitalWrite(BUZZER_PIN, buzzerState ? HIGH : LOW);
      lastBeep = now;
    }
  } else {
    digitalWrite(LED_PIN, LOW);
    digitalWrite(BUZZER_PIN, LOW);
    buzzerState = false;
  }

  // --- Confirmed flame ---
  if (flameDetected) { if (flameSince == 0) flameSince = now; } else { flameSince = 0; }
  bool flameConfirmed = (flameSince != 0) && (now - flameSince >= FLAME_CONFIRM_MS);

  // --- Automatic pump (only if automatic mode is armed) ---
  if (!autoMode && pumpRunning) {
    pumpRunning = false; pumpEverRan = true; pumpStop = now;
    Serial.println("[PUMP] Automatic run cancelled (mode disarmed)");
  }
  if (!pumpRunning) {
    bool cooldownOver = !pumpEverRan || (now - pumpStop >= PUMP_COOLDOWN_MS);
    if (autoMode && flameConfirmed && cooldownOver) {
      pumpRunning = true; pumpStart = now;
      Serial.println("[PUMP] Flame confirmed, automatic extinguishing started");
    }
  } else if (now - pumpStart >= PUMP_RUN_MS) {
    pumpRunning = false; pumpEverRan = true; pumpStop = now;
    Serial.println("[PUMP] Automatic run finished");
  }

  // --- Relay: automatic OR manual pump ---
  bool pumpWanted = pumpRunning || manualPump;
  if (pumpWanted != relayState) {
    relayState = pumpWanted;
    if (relayState) pumpOn(); else pumpOff();
    Serial.print(relayState ? ">>> PUMP ON" : ">>> PUMP OFF");
    Serial.println(relayState ? (manualPump ? " (manual)" : " (automatic)") : "");
  }

  printStatus(now, smokeValue, flameDetected, smokeDetected, alarm, manualPump);
  network(smokeValue, flameDetected, smokeDetected, relayState);
}
