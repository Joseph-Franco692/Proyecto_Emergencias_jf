#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <LiquidCrystal_I2C.h>

// ─── CONFIGURACIÓN ───────────────────────────────────────────────────────────
// Wokwi: WIFI_SSID = "Wokwi-GUEST" y WIFI_PASS = ""
// Equipo real: coloca aquí el nombre y contraseña del punto de acceso.
const char* WIFI_SSID = "TU_WIFI";
const char* WIFI_PASS = "TU_PASSWORD";

// Usa siempre la URL HTTPS que muestra ngrok, sin barra final.
const char* API_BASE_URL = "https://TU-DOMINIO.ngrok-free.dev";
const char* NODO_ID = "NODO-ESP32-BOMBEROS-01";

// Debe coincidir con IOT_NODE_KEY en el entorno de Spring Boot.
const char* IOT_NODE_KEY = "CAMBIAR-CLAVE-NODO-2026";

const int PIN_GAS = 34;
const int PIN_BUZZER = 23;
const int PIN_SWITCH = 4;
const int PIN_RGB_ROJO = 5;
// Estos dos valores corresponden al diagram.json suministrado.
const int PIN_RGB_VERDE = 18;
const int PIN_RGB_AZUL = 19;

// Ajusta únicamente estas dos líneas después de observar el valor normal
// del sensor en el monitor serial.
const int UMBRAL_ADVERTENCIA = 500;
const int UMBRAL_PELIGRO = 600;
const unsigned long INTERVALO_ENVIO_MS = 5000;
const unsigned long INTERVALO_LCD_MS = 400;
const unsigned long REBOTE_BOTON_MS = 250;
const unsigned long REINTENTO_WIFI_MS = 10000;

LiquidCrystal_I2C lcd(0x27, 16, 2);

volatile bool sistemaEncendido = false;
volatile bool alarmaCritica = false;
bool botonAnterior = HIGH;
int estadoVisualAnterior = -1;
unsigned long ultimoCambioBoton = 0;
unsigned long ultimoEnvio = 0;
unsigned long ultimaPantalla = 0;
unsigned long ultimoIntentoWifi = 0;

// Ritmo claro "corto, corto, largo" ejecutado en un núcleo independiente.
const unsigned int patronAlarma[][2] = {
  {100, 100},
  {100, 140},
  {380, 550}
};
const int pasosAlarma = sizeof(patronAlarma) / sizeof(patronAlarma[0]);

void fijarColorRGB(bool rojo, bool verde, bool azul) {
  digitalWrite(PIN_RGB_ROJO, rojo);
  digitalWrite(PIN_RGB_VERDE, verde);
  digitalWrite(PIN_RGB_AZUL, azul);
}

void conectarWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;
  Serial.printf("[WIFI] Conectando a %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - inicio < 12000) {
    delay(250);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    WiFi.setSleep(false);
    WiFi.setAutoReconnect(true);
    Serial.printf("[WIFI] Conectado. IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("[WIFI] Sin conexión. Se reintentará automáticamente.");
  }
}

String estadoAire(int nivelGas) {
  if (nivelGas >= UMBRAL_PELIGRO) return "CRITICO";
  if (nivelGas >= UMBRAL_ADVERTENCIA) return "PRECAUCION";
  return "RESPIRABLE";
}

void actualizarAlarma(bool critica) {
  alarmaCritica = critica && sistemaEncendido;
}

void tareaAlarma(void* parametro) {
  while (true) {
    if (!sistemaEncendido || !alarmaCritica) {
      digitalWrite(PIN_BUZZER, LOW);
      vTaskDelay(pdMS_TO_TICKS(20));
      continue;
    }

    for (int i = 0; i < pasosAlarma && sistemaEncendido && alarmaCritica; i++) {
      digitalWrite(PIN_BUZZER, HIGH);
      vTaskDelay(pdMS_TO_TICKS(patronAlarma[i][0]));
      digitalWrite(PIN_BUZZER, LOW);
      vTaskDelay(pdMS_TO_TICKS(patronAlarma[i][1]));
    }
  }
}

void mostrarEstado(int nivelGas) {
  if (millis() - ultimaPantalla < INTERVALO_LCD_MS) return;
  ultimaPantalla = millis();

  int estadoVisual = !sistemaEncendido ? 0
                    : nivelGas >= UMBRAL_PELIGRO ? 1
                    : nivelGas >= UMBRAL_ADVERTENCIA ? 2 : 3;

  if (estadoVisual != estadoVisualAnterior) {
    lcd.clear();
    estadoVisualAnterior = estadoVisual;
  }

  if (estadoVisual == 0) {
    fijarColorRGB(LOW, LOW, HIGH);
    lcd.setCursor(0, 0); lcd.print("  NODO TACTICO  ");
    lcd.setCursor(0, 1); lcd.print("SISTEMA APAGADO ");
    return;
  }

  lcd.setCursor(0, 0);
  lcd.print("HUMO:");
  lcd.print(nivelGas);
  lcd.print("       ");
  lcd.setCursor(0, 1);

  if (estadoVisual == 1) {
    fijarColorRGB(HIGH, LOW, LOW);
    lcd.print("CRITICO EVACUAR ");
  } else if (estadoVisual == 2) {
    fijarColorRGB(HIGH, HIGH, LOW);
    lcd.print("PRECAUCION      ");
  } else {
    fijarColorRGB(LOW, HIGH, LOW);
    lcd.print("AIRE RESPIRABLE ");
  }
}

bool enviarTelemetria(const char* evento, int nivelGas) {
  if (WiFi.status() != WL_CONNECTED) return false;

  WiFiClientSecure client;
  // Prototipo ngrok: en producción instalar la CA raíz y quitar setInsecure().
  client.setInsecure();
  client.setTimeout(15000);

  HTTPClient http;
  String url = String(API_BASE_URL) + "/api/iot/telemetria";
  if (!http.begin(client, url)) {
    Serial.println("[IOT] No se pudo iniciar HTTPS");
    return false;
  }

  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-IOT-KEY", IOT_NODE_KEY);
  http.addHeader("ngrok-skip-browser-warning", "1");
  http.setTimeout(15000);

  String payload = "{";
  payload += "\"nodoId\":\"" + String(NODO_ID) + "\",";
  payload += "\"nivelGas\":" + String(nivelGas) + ",";
  payload += "\"umbralAdvertencia\":" + String(UMBRAL_ADVERTENCIA) + ",";
  payload += "\"umbralPeligro\":" + String(UMBRAL_PELIGRO) + ",";
  payload += "\"evento\":\"" + String(evento) + "\"";
  payload += "}";

  int codigo = http.POST(payload);
  String respuesta = codigo > 0 ? http.getString() : http.errorToString(codigo);
  Serial.printf("[IOT] %s gas=%d HTTP=%d %s\n",
                evento, nivelGas, codigo, respuesta.c_str());
  http.end();
  return codigo >= 200 && codigo < 300;
}

void procesarBoton(int nivelGas) {
  bool lectura = digitalRead(PIN_SWITCH);
  unsigned long ahora = millis();

  if (botonAnterior == HIGH && lectura == LOW
      && ahora - ultimoCambioBoton >= REBOTE_BOTON_MS) {
    ultimoCambioBoton = ahora;
    sistemaEncendido = !sistemaEncendido;
    estadoVisualAnterior = -1;

    if (sistemaEncendido) {
      Serial.println("[IOT] Nodo encendido");
      enviarTelemetria("INICIO_SESION", nivelGas);
      ultimoEnvio = ahora;
    } else {
      Serial.println("[IOT] Nodo apagado");
      enviarTelemetria("FIN_SESION", nivelGas);
      digitalWrite(PIN_BUZZER, LOW);
    }
  }
  botonAnterior = lectura;
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_RGB_ROJO, OUTPUT);
  pinMode(PIN_RGB_VERDE, OUTPUT);
  pinMode(PIN_RGB_AZUL, OUTPUT);
  pinMode(PIN_GAS, INPUT);
  pinMode(PIN_SWITCH, INPUT_PULLUP);

  xTaskCreatePinnedToCore(
    tareaAlarma,
    "alarma_buzzer",
    2048,
    nullptr,
    1,
    nullptr,
    0
  );

  lcd.init();
  lcd.backlight();
  lcd.setCursor(0, 0); lcd.print(" INICIANDO NODO ");
  lcd.setCursor(0, 1); lcd.print("CONECTANDO WIFI ");
  conectarWiFi();
  lcd.clear();
}

void loop() {
  unsigned long ahora = millis();
  int nivelGas = analogRead(PIN_GAS);

  if (WiFi.status() != WL_CONNECTED
      && ahora - ultimoIntentoWifi >= REINTENTO_WIFI_MS) {
    ultimoIntentoWifi = ahora;
    conectarWiFi();
  }

  procesarBoton(nivelGas);
  mostrarEstado(nivelGas);
  actualizarAlarma(nivelGas >= UMBRAL_PELIGRO);

  if (sistemaEncendido && ahora - ultimoEnvio >= INTERVALO_ENVIO_MS) {
    ultimoEnvio = ahora;
    enviarTelemetria("TELEMETRIA", nivelGas);
  }

  delay(10);
}
