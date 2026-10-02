# Cafetera — estado del desarrollo

Documento para retomar el trabajo. Última instalación en el teléfono: 1 de octubre de 2026.

Hay dos proyectos relacionados:

| Proyecto | Carpeta | Rol |
| --- | --- | --- |
| Cafetera | `C:\develop\cafetera` | Aplicación Android. Un botón publica un mensaje MQTT. |
| Radar MQTT | `C:\develop\radar-mqtt` | Programa de Windows para escuchar el broker, con brokers y payloads guardados. |

El teléfono envía el pulso. Radar, u otro suscriptor, lo recibe.

## Contrato MQTT actual

Definido en `config.properties`, en la raíz de Cafetera:

```properties
mqtt.broker=tcp://test.mosquitto.org:1883
mqtt.topic=cl/kope/iot/cafetera
mqtt.payload={"action": "turn-on","pulso_ms": 500}
mqtt.qos=2
mqtt.clientId=cafetera-kp
mqtt.username=
mqtt.password=
```

Ese archivo se copia al APK en cada compilación (`copyMqttConfig` en `app/build.gradle.kts`). Modificar el archivo no actualiza la aplicación ya instalada: es necesario volver a compilar e instalar.

El valor de `mqtt.payload` es texto literal. Java `Properties` no usa comillas como delimitador. Una línea como `mqtt.payload="hola"` envía las comillas. No se debe repetir la clave (`mqtt.payload=mqtt.payload=...`): el texto sobrante queda dentro del mensaje.

Esquemas aceptados en `mqtt.broker`: `tcp://`, `ssl://`, `ws://`, `wss://`. El manifiesto permite tráfico sin cifrar (`usesCleartextTraffic`) porque el broker de prueba usa `tcp://`.

## Aplicación Android

- Nombre visible: Cafetera
- `applicationId`: `com.cafetera`
- minSdk 26, compileSdk/targetSdk 35, Java/Kotlin 17
- Gradle 8.11.1, Android Gradle Plugin 8.7.3, Kotlin 2.0.21
- MQTT: Eclipse Paho `org.eclipse.paho.client.mqttv3:1.2.5`
- Interfaz en XML con View Binding, sin Compose
- Teléfono de prueba: Motorola Edge 60 Fusion (`ZY22LGGBGV`)

### Pantallas

1. `SplashActivity`: pantalla de inicio del sistema (icono de taza sobre fondo café) y, a continuación, una pantalla propia con la taza, «Cafetera» y «Calentando el agua…». A los 1,7 s abre `MainActivity`.
2. `MainActivity`: taza, botón **Hacer café** y el broker, el tópico y el mensaje leídos de la configuración. El botón publica y muestra el resultado o el error.

Iconos vectoriales: `res/drawable/ic_coffee.xml` (pantallas) e `ic_launcher_foreground.xml` (icono de la aplicación).

### Publicación

`MqttPublisher` se conecta, publica y se desconecta. No mantiene una sesión abierta.

- Cada envío usa un identificador de cliente distinto: `{mqtt.clientId}-{nanoTime en hexadecimal}`. Con el identificador fijo `cafetera-kp`, el broker cerraba el socket en el primer intento al volver a abrir la aplicación. Paho lo informa como «Se ha perdido la conexión», código 32109, causa `EOFException`.
- Si falla por pérdida de conexión, tiempo de espera agotado o error de red, reintenta hasta 3 veces, con esperas de 700 ms y luego 1400 ms.
- Sesión limpia, MQTT 3.1.1, tiempo de espera de la operación 10 s, keepalive 30 s.
- El QoS y el texto se envían tal como están en `config.properties`. El mensaje no queda retenido en el broker.

Código relevante:

- `app/src/main/java/com/cafetera/MqttConfig.kt`
- `app/src/main/java/com/cafetera/MqttPublisher.kt`
- `app/src/main/java/com/cafetera/MainActivity.kt`
- `app/src/main/java/com/cafetera/SplashActivity.kt`

### Compilar e instalar

En este equipo ya están el JDK 17, el Android SDK en `C:\Users\edoko\AppData\Local\Android\Sdk` (`local.properties`) y el teléfono con depuración USB.

```powershell
cd C:\develop\cafetera
$env:ANDROID_HOME = "C:\Users\edoko\AppData\Local\Android\Sdk"
.\gradlew.bat :app:installDebug
```

`adb devices` debe mostrar el teléfono como `device`.

## Radar MQTT

Escucha MQTT en Windows. No se conecta al abrirse: es necesario pulsar **Comenzar a escuchar**.

- Brokers guardados: host, puerto, usuario, contraseña, TLS, identificador de cliente, tópicos (uno por línea) y QoS.
- Payloads guardados con nombre. Si el mensaje coincide, el registro lo marca con ese nombre. Hay una opción para mostrar solo esos payloads.
- Los datos se guardan en `%APPDATA%\RadarMqtt\config.json`, no en el repositorio.
- El primer inicio crea un broker de ejemplo (`test.mosquitto.org`, tópico `cafetera/hacer`, payload `hacer`). Esa configuración ya no coincide con el tópico actual `cl/kope/iot/cafetera`. Para ver el pulso de la cafetera, hay que indicar ese tópico en Radar, o editar el JSON si el programa ya se abrió antes.

Ejecutable: `C:\develop\radar-mqtt\dist\RadarMqtt.exe`.

Para generarlo de nuevo:

```powershell
cd C:\develop\radar-mqtt
.\build.bat
```

`build.bat` crea `.venv`, instala `paho-mqtt` y PyInstaller, y genera el ejecutable. También se puede ejecutar sin empaquetar:

```powershell
.\.venv\Scripts\python.exe main.py
```

Código: `main.py`, `radar_mqtt/app.py` (ventana Tkinter), `radar_mqtt/storage.py`.

## Pendiente

- Cafetera se versiona en su propio repositorio. Radar MQTT está en `https://github.com/edokope4/radar-mqtt`.
- No hay una variante de release firmada. Solo la versión de depuración, instalada con `installDebug`.
- Radar no está actualizado al tópico `cl/kope/iot/cafetera` ni al JSON `{"action":"turn-on","pulso_ms":500}`.
- El broker sigue siendo el público `test.mosquitto.org`. El usuario y la contraseña están vacíos.
- El identificador de cliente configurado es solo un prefijo. El identificador real de cada conexión lleva un sufijo.
