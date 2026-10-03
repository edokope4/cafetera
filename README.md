# Cafetera

Aplicación Android para pedir un café. Un botón publica un mensaje MQTT. Cuando el café está listo, la taza muestra un ticket verde y la hora del aviso.

El nombre del lanzador es Cafetera. En la pantalla principal el título es Cafecito y el botón dice **Café por favor**.

Paquete: `com.cafetera`. minSdk 26, compileSdk y targetSdk 35.

## Piezas

| Pieza | Repositorio | Rol |
| --- | --- | --- |
| Esta app | [cafetera](https://github.com/edokope4/cafetera) | Publica el pedido y muestra el estado en la taza |
| Placa ESP8266 | [arduino-cafetera](https://github.com/edokope4/arduino-cafetera) | Escucha el pedido |
| Radar MQTT | [radar-mqtt](https://github.com/edokope4/radar-mqtt) | Cliente de Windows para escuchar y publicar |
| Backend | imagen `edokope/cafetera-under-backend` | Al recibir el café listo, avisa al teléfono y publica el apagado |

## Flujo

1. **Café por favor** publica en `cl/kope/iot/cafetera`.
2. La placa recibe ese pedido.
3. Al terminar, alguien publica en `cl/kope/iot/cafetera/status` que el café está listo.
4. El backend envía un aviso al teléfono y publica la orden de apagado en `cl/kope/iot/cafetera`.
5. Si la app está abierta, la taza pasa al ticket y no aparece el banner. Si está cerrada, el aviso abre la app ya con el ticket.

## MQTT

Broker de prueba: `tcp://test.mosquitto.org:1883`. La configuración está en `config.properties` y se copia dentro del APK en cada compilación. Cambiar ese archivo no actualiza la app ya instalada: hay que volver a compilar e instalar.

| Tópico | Mensaje | Quién lo envía |
| --- | --- | --- |
| `cl/kope/iot/cafetera` | `{"action": "turn-on","pulso_ms": 500}` | Esta app, con QoS 2 |
| `cl/kope/iot/cafetera/status` | `{"code": 4, "message": "Cafe listo"}` | La cafetera |
| `cl/kope/iot/cafetera` | `{"code": 4, "message": "Cafe listo", "action": "turn-off"}` | El backend |

`mqtt.payload` es texto literal. Java `Properties` no trata las comillas como delimitador: si se escriben, se envían. El primer `=` de la línea separa la clave del valor.

Cada envío usa un identificador de cliente distinto, con el prefijo `mqtt.clientId`. La app se conecta, publica y se desconecta. El mensaje no queda retenido.

`debug=true` muestra en pantalla el broker, el tópico, el mensaje y el token de avisos. Con `debug=false` esas líneas quedan ocultas.

## La taza

| Momento | Qué se ve |
| --- | --- |
| Recién abierta | Taza vacía |
| Botón pulsado | Taza con una nota |
| Pedido publicado | Taza con un reloj |
| Llega el aviso | Ticket verde y la hora, por ejemplo «Listo a las 10:05» |
| Falla el envío | Taza vacía |

La hora llega en el dato `ready_at` del aviso, en UTC, y se muestra en la zona del teléfono. Si ese dato no viene, se usa la hora en que el teléfono recibió el aviso.

Pulsar el botón otra vez quita la marca. Cerrar la app y volver a abrirla también. El ticket solo aparece cuando llega el aviso, o al abrir la app desde ese aviso.

Con la app en primer plano no se muestra el banner del sistema. Con la app cerrada, en Android reciente hace falta el permiso de mostrar sobre otras aplicaciones para abrirla sola. Sin ese permiso, tocar el aviso igual abre la taza con el ticket.

## Compilar

Hace falta JDK 17 y el Android SDK. `local.properties` debe apuntar al SDK.

```powershell
.\gradlew.bat :app:installDebug
```

El lanzador es `SplashActivity`. `MainActivity` no está exportada.

Para recibir avisos hace falta `app/google-services.json` en la máquina de compilación. Ese archivo no se versiona: tiene la clave de Firebase. Sin él, la app compila y publica el pedido, pero no recibe el café listo. La cuenta de servicio tampoco se versiona; vive en `secrets/`.

El detalle para retomar el trabajo está en [doc/desarrollo.md](doc/desarrollo.md).
