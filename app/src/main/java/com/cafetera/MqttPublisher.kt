package com.cafetera

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.IOException

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
                        val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                        callback(false, detail)
                        return@Thread
                    }
                    Thread.sleep(700L * attempt)
                }
            }
            val detail = lastError?.message?.takeIf { it.isNotBlank() } ?: "Error al publicar"
            callback(false, detail)
        }.start()
    }

    private fun connect(config: MqttConfig): MqttClient {
        val suffix = java.lang.Long.toHexString(System.nanoTime())
        val clientId = "${config.clientId}-$suffix"
        val client = MqttClient(config.broker, clientId, MemoryPersistence())
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
