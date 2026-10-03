# Cafetera — estado del desarrollo

Documento para retomar el trabajo. Actualizado el 3 de octubre de 2026.

La cafetera son tres proyectos. Este repositorio es la aplicación Android.

| Proyecto | Carpeta | Rol |
| --- | --- | --- |
| Cafetera | `C:\develop\cafetera` | App Android. Publica el pedido y muestra el estado en la taza. |
| Radar MQTT | `C:\develop\radar-mqtt` | Cliente de Windows para escuchar y publicar. Ver `doc/desarrollo.md` de ese proyecto. |
| cafetera-under-backend | `C:\develop\cafetera-under-backend` | Servicio que, al recibir «Cafe listo», envía el aviso push y publica `turn-off`. |

Repositorio: https://github.com/edokope4/cafetera

## Flujo

1. En el teléfono, **Café por favor** publica en `cl/kope/iot/cafetera` el JSON `{"action": "turn-on","pulso_ms": 500}`.
2. La máquina (u otro publicador) avisa en `cl/kope/iot/cafetera/status` con `{"code": 4, "message": "Cafe listo"}`.
3. El backend envía un mensaje de datos de FCM al teléfono y republica en `cl/kope/iot/cafetera` el mismo JSON con `"action": "turn-off"`.
4. Si la app está abierta, la taza pasa al ticket verde y no aparece el banner. Si está cerrada, el aviso abre la app ya con el ticket.

## Contrato MQTT de la app

Definido en `config.properties`, en la raíz:

```properties
mqtt.broker=tcp://test.mosquitto.org:1883
mqtt.topic=cl/kope/iot/cafetera
mqtt.payload={"action": "turn-on","pulso_ms": 500}
mqtt.qos=2
mqtt.clientId=cafetera-kp
mqtt.username=
mqtt.password=
debug=false
```

Ese archivo se copia al APK en cada compilación (`copyMqttConfig` en `app/build.gradle.kts`). Cambiarlo no actualiza la aplicación ya instalada: hay que volver a compilar e instalar.

El valor de `mqtt.payload` es texto literal. Java `Properties` no usa comillas como delimitador. Una línea como `mqtt.payload="hola"` envía las comillas. No se debe repetir la clave: el texto sobrante queda dentro del mensaje. El primer `=` sin escapar es el separador.

`debug=true` muestra en pantalla el broker, el tópico, el mensaje y el token de FCM. Con `debug=false` esas líneas quedan ocultas. El permiso de notificaciones del sistema solo se pide cuando el modo debug está activo y Firebase está configurado.

Esquemas aceptados en `mqtt.broker`: `tcp://`, `ssl://`, `ws://`, `wss://`. El manifiesto permite tráfico sin cifrar (`usesCleartextTraffic`) porque el broker de prueba usa `tcp://`.

## Aplicación Android

- Nombre del lanzador, del splash y del aviso: Cafetera
- Título de la pantalla principal: Cafecito
- `applicationId`: `com.cafetera`
- minSdk 26, compileSdk/targetSdk 35, Java/Kotlin 17
- Gradle 8.11.1, Android Gradle Plugin 8.7.3, Kotlin 2.0.21
- MQTT: Eclipse Paho `org.eclipse.paho.client.mqttv3:1.2.5`
- Firebase Cloud Messaging, BOM 33.7.0. El plugin de Google Services se aplica solo si existe `app/google-services.json`
- Interfaz en XML con View Binding, sin Compose
- Teléfono de prueba: Motorola Edge 60 Fusion (`ZY22LGGBGV`), Android 16

### Pantallas

1. `SplashActivity` es el lanzador. Muestra la taza, «Cafetera» y «Calentando el agua…», y a los 1,7 s abre `MainActivity`.
2. `MainActivity` no se exporta. Muestra la taza centrada y el botón **Café por favor**. Al abrirla a mano, la taza está vacía.

Estados de la taza, un solo `ImageView` sobre el dibujo:

| Momento | Marca | Texto |
| --- | --- | --- |
| Recién abierta | Taza vacía | Texto de reposo |
| Botón pulsado, envío en curso | Nota de papel | Enviando… |
| MQTT publicado | Reloj | Pedido enviado. El café está en camino. |
| Llega el aviso | Ticket verde | Texto del mensaje, por ejemplo Cafe listo |
| El envío falla | Taza vacía | El error |

Pulsar el botón otra vez quita la marca. Cerrar la app y volver a abrirla también la quita: el ticket solo aparece cuando llega el aviso, o al abrirla desde ese aviso.

Iconos: `ic_coffee.xml`, `ic_cup_note.xml`, `ic_cup_clock.xml`, `ic_ready_check.xml`, `ic_launcher_foreground.xml`.

### Publicación

`MqttPublisher` se conecta, publica y se desconecta. No mantiene una sesión abierta.

- Cada envío usa un identificador de cliente distinto: `{mqtt.clientId}-{nanoTime en hexadecimal}`. Con el identificador fijo `cafetera-kp`, el broker cerraba el socket en el primer intento al volver a abrir la aplicación.
- Si falla por pérdida de conexión, tiempo de espera agotado o error de red, reintenta hasta 3 veces, con esperas de 700 ms y luego 1400 ms.
- Sesión limpia, MQTT 3.1.1, tiempo de espera de la operación 10 s, keepalive 30 s.
- El mensaje no queda retenido. Si el ticket ya se mostró, un éxito tardío de MQTT no lo reemplaza por el reloj.

### Aviso de café listo

El backend manda un mensaje de datos de FCM, prioridad alta, sin bloque `notification`. Así `CoffeeMessagingService.onMessageReceived` siempre corre.

- Con la actividad iniciada, la taza pasa al ticket, se muestra la hora del aviso y se cancelan las notificaciones de la app. No se muestra el banner. La hora llega en el dato `ready_at` del backend y se presenta en la zona horaria del teléfono. Si ese dato no viene, se usa la hora en que el teléfono recibió el aviso.
- Con la app cerrada, se muestra el aviso. Al tocarlo, o si el sistema deja traer la actividad al frente, `MainActivity` abre con el ticket.
- En Android 16 una app normal no puede abrirse sola en segundo plano. Hace falta el permiso «mostrar sobre otras aplicaciones» (`SYSTEM_ALERT_WINDOW`). En el teléfono de prueba se concedió con `adb shell appops set com.cafetera SYSTEM_ALERT_WINDOW allow`. Si falta, la app lo pide una sola vez. Sin ese permiso, el aviso sigue abriendo la taza con el ticket al tocarlo.
- No usar `adb shell am force-stop com.cafetera` para probar avisos: el sistema deja de entregar FCM hasta que alguien abre la app. Para cerrarla en una prueba, ir a inicio y usar `adb shell am kill com.cafetera`.

Código relevante:

- `app/src/main/java/com/cafetera/MainActivity.kt`
- `app/src/main/java/com/cafetera/MqttConfig.kt`
- `app/src/main/java/com/cafetera/MqttPublisher.kt`
- `app/src/main/java/com/cafetera/SplashActivity.kt`
- `app/src/main/java/com/cafetera/CoffeeMessagingService.kt`
- `app/src/main/java/com/cafetera/CoffeeNotifications.kt`
- `app/src/main/java/com/cafetera/CoffeeReady.kt`

### Compilar e instalar

En este equipo ya están el JDK 17, el Android SDK en `C:\Users\edoko\AppData\Local\Android\Sdk` (`local.properties`) y el teléfono con depuración USB.

```powershell
cd C:\develop\cafetera
$env:ANDROID_HOME = "C:\Users\edoko\AppData\Local\Android\Sdk"
.\gradlew.bat :app:installDebug
```

`adb devices` debe mostrar el teléfono como `device`. Si `:app:processDebugResources` no puede escribir `R.jar`, el servidor de lenguaje de Kotlin lo tiene abierto. Hay que cerrar solo ese proceso Java, borrar el jar y repetir la instalación.

El lanzador es `SplashActivity`. Arrancar `MainActivity` con adb falla porque no está exportada.

### Lo que no se versiona

- `secrets/` — cuenta de servicio de Firebase. No subirla.
- `app/google-services.json` — clave de la app de Firebase. Sin ese archivo local, la app compila pero no recibe avisos. El plugin de Google Services se omite si el archivo no está.

## Pendiente

- No hay una variante de release firmada. Solo la versión de depuración, instalada con `installDebug`.
- El broker sigue siendo el público `test.mosquitto.org`. El usuario y la contraseña están vacíos.
- El identificador de cliente configurado es solo un prefijo. El identificador real de cada conexión lleva un sufijo.
- El texto de reposo de la pantalla todavía usa voseo («Tocá el botón…»).
