package com.cafetera

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI

object MqttPublisher {
    private const val MAX_ATTEMPTS = 3
    private val retryableReasons = setOf(32000, 32002, 32103, 32104, 32109)

    fun publish(config: MqttConfig, callback: (success: Boolean, detail: String?) -> Unit) {
        Thread {
            var lastError: Exception? = null
            for (attempt in 1..MAX_ATTEMPTS) {
                var client: MqttClient? = null
                try {
                    client = connect(config)
                    deliver(client, config)
                    closeQuietly(client)
                    callback(true, null)
                    return@Thread
                } catch (error: Exception) {
                    lastError = error
                    closeQuietly(client)
                    if (attempt == MAX_ATTEMPTS || !isRetryable(error)) {
                        callback(false, errorDetail(error))
                        return@Thread
                    }
                    Thread.sleep(700L * attempt)
                }
            }
            callback(false, lastError?.let(::errorDetail) ?: "Error al publicar")
        }.start()
    }

    private fun connect(config: MqttConfig): MqttClient {
        var lastError: Exception? = null
        for (broker in brokerCandidates(config.broker)) {
            try {
                return open(broker, config)
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IOException("No se pudo resolver el broker")
    }

    private fun open(broker: String, config: MqttConfig): MqttClient {
        val suffix = java.lang.Long.toHexString(System.nanoTime())
        val clientId = "${config.clientId}-$suffix"
        val client = MqttClient(broker, clientId, MemoryPersistence())
        client.timeToWait = 10_000
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 8
            keepAliveInterval = 30
            isAutomaticReconnect = false
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            if (config.username.isNotEmpty()) {
                userName = config.username
                password = config.password.toCharArray()
            }
        }
        client.connect(options)
        return client
    }

    private fun brokerCandidates(broker: String): List<String> {
        val uri = try {
            URI(broker)
        } catch (_: Exception) {
            return listOf(broker)
        }
        val host = uri.host ?: return listOf(broker)
        val scheme = uri.scheme ?: return listOf(broker)
        val port = if (uri.port > 0) uri.port else if (scheme == "ssl" || scheme == "wss") 8883 else 1883
        val addresses = try {
            InetAddress.getAllByName(host).sortedBy { address -> if (address is Inet4Address) 0 else 1 }
        } catch (_: Exception) {
            return listOf(broker)
        }
        if (addresses.isEmpty()) return listOf(broker)
        return addresses.map { address ->
            val literal = if (address.hostAddress?.contains(':') == true) {
                "[${address.hostAddress}]"
            } else {
                address.hostAddress
            }
            "$scheme://$literal:$port"
        }
    }

    private fun errorDetail(error: Exception): String {
        val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        val cause = error.cause?.message?.takeIf { it.isNotBlank() && it != message }
        return if (cause == null) message else "$message ($cause)"
    }

    private fun deliver(client: MqttClient, config: MqttConfig) {
        val message = MqttMessage(config.payload.toByteArray(Charsets.UTF_8)).apply {
            qos = config.qos
            isRetained = false
        }
        client.publish(config.topic, message)
    }

    private fun isRetryable(error: Exception): Boolean {
        if (error is IOException) return true
        if (error is MqttException) {
            if (error.reasonCode in retryableReasons) return true
            if (error.cause is IOException) return true
        }
        return false
    }

    private fun closeQuietly(client: MqttClient?) {
        if (client == null) return
        try {
            if (client.isConnected) {
                client.disconnect(2_000)
            }
        } catch (_: Exception) {
        }
        try {
            client.close()
        } catch (_: Exception) {
        }
    }
}
