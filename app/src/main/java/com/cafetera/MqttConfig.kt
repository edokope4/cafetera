package com.cafetera

import android.content.Context
import java.io.InputStreamReader
import java.util.Properties

data class MqttConfig(
    val broker: String,
    val topic: String,
    val payload: String,
    val qos: Int,
    val clientId: String,
    val username: String,
    val password: String,
    val debug: Boolean,
) {
    companion object {
        fun load(context: Context): MqttConfig {
            val properties = Properties()
            context.assets.open("config.properties").use { input ->
                InputStreamReader(input, Charsets.UTF_8).use { reader ->
                    properties.load(reader)
                }
            }

            fun required(key: String): String {
                val value = properties.getProperty(key)?.trim().orEmpty()
                if (value.isEmpty()) {
                    throw IllegalStateException("falta $key")
                }
                return value
            }

            val broker = required("mqtt.broker")
            val scheme = broker.substringBefore("://")
            if (scheme !in setOf("tcp", "ssl", "ws", "wss")) {
                throw IllegalStateException("mqtt.broker debe empezar con tcp://, ssl://, ws:// o wss://")
            }

            val qos = properties.getProperty("mqtt.qos")?.trim()?.toIntOrNull()?.coerceIn(0, 2) ?: 1
            val clientId = properties.getProperty("mqtt.clientId")?.trim().orEmpty().ifBlank { "cafetera" }

            return MqttConfig(
                broker = broker,
                topic = required("mqtt.topic"),
                payload = properties.getProperty("mqtt.payload")?.trim().orEmpty().ifBlank { "hacer" },
                qos = qos,
                clientId = clientId,
                username = properties.getProperty("mqtt.username")?.trim().orEmpty(),
                password = properties.getProperty("mqtt.password")?.trim().orEmpty(),
                debug = properties.getProperty("debug")?.trim().equals("true", ignoreCase = true),
            )
        }
    }
}
