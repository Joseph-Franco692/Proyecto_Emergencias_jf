# Nodo táctico ESP32 post-incendio

## Flujo de uso

1. Spring Boot debe estar ejecutándose en el puerto `8081`.
2. Inicia el túnel con `ngrok http 8081`.
3. Copia la URL HTTPS entregada por ngrok en `API_BASE_URL` del sketch.
4. Usa el mismo valor para `IOT_NODE_KEY` en Spring Boot y `IOT_NODE_KEY`
   en el sketch.
5. Carga el firmware al ESP32.
6. El operador confirma llegada al sitio.
7. En el panel del operador pulsa **INICIAR EVALUACIÓN IOT** usando
   `NODO-ESP32-BOMBEROS-01`.
8. Enciende el nodo con el pulsador físico.

El ESP32 no contiene el ID del reporte. El backend relaciona el `NODO_ID` con
la emergencia activa del operador, por lo que el mismo firmware sirve para
todas las salidas.

## Variables recomendadas

Antes de iniciar Spring Boot en PowerShell:

```powershell
$env:IOT_NODE_KEY="UNA-CLAVE-LARGA-Y-PRIVADA"
mvn spring-boot:run
```

Coloca exactamente la misma clave en el sketch:

```cpp
const char* IOT_NODE_KEY = "UNA-CLAVE-LARGA-Y-PRIVADA";
```

No publiques la contraseña Wi-Fi, la clave del nodo ni el token de ngrok en
GitHub.

## Wokwi

Para la simulación:

```cpp
const char* WIFI_SSID = "Wokwi-GUEST";
const char* WIFI_PASS = "";
```

El diagrama suministrado ya coincide con los pines del sketch:

- Gas analógico: GPIO 34
- Buzzer: GPIO 23
- Pulsador: GPIO 4 con `INPUT_PULLUP`
- RGB: rojo GPIO 5, verde GPIO 18 y azul GPIO 19
- LCD I2C: SDA 21, SCL 22, dirección `0x27`

## Criterio de habitabilidad

El dispositivo conserva los avisos locales con umbrales 2500 y 2600. El
backend no declara `HABITABLE` por una sola muestra: exige cinco lecturas
consecutivas por debajo de 2500. Una lectura crítica produce
`NO_HABITABLE`.

## Precauciones del prototipo físico

- El JSON de Wokwi conecta verde a GPIO 18 y azul a GPIO 19. Si el montaje
  real está cableado al revés, intercambia únicamente esas dos constantes.
- Verifica con un multímetro la tensión máxima de `AOUT` del módulo de gas.
  Un GPIO del ESP32 no debe recibir 5 V. Si el módulo puede entregar 5 V,
  utiliza un divisor resistivo que limite la señal a un máximo de 3.3 V.
- Un LED RGB debería tener una resistencia limitadora por cada canal.
- Los sensores de la familia MQ necesitan precalentamiento y calibración.
- Este montaje entrega una evaluación indicativa para el prototipo académico.
  Un único sensor genérico de humo/gas no certifica la habitabilidad de una
  estructura ni sustituye mediciones profesionales de CO, O2, partículas,
  temperatura y gases tóxicos.
