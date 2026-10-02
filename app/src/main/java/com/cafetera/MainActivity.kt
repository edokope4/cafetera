package com.cafetera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cafetera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var config: MqttConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            val loaded = MqttConfig.load(this)
            config = loaded
            binding.configSummary.text = listOf(
                getString(R.string.broker_line, loaded.broker),
                getString(R.string.topic_line, loaded.topic),
                getString(R.string.payload_line, loaded.payload),
            ).joinToString("\n")
        } catch (error: Exception) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            binding.status.text = getString(R.string.config_error, detail)
            binding.status.setTextColor(ContextCompat.getColor(this, R.color.error))
            binding.brewButton.isEnabled = false
        }

        binding.brewButton.setOnClickListener {
            val current = config ?: return@setOnClickListener
            binding.brewButton.isEnabled = false
            binding.brewButton.text = getString(R.string.sending)
            binding.status.text = getString(R.string.status_sending)
            binding.status.setTextColor(ContextCompat.getColor(this, R.color.ink))

            MqttPublisher.publish(current) { success, detail ->
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    binding.brewButton.isEnabled = true
                    binding.brewButton.text = getString(R.string.make_coffee)
                    if (success) {
                        binding.status.text = getString(R.string.status_ok)
                        binding.status.setTextColor(ContextCompat.getColor(this, R.color.ok))
                    } else {
                        binding.status.text = getString(R.string.status_error, detail ?: "")
                        binding.status.setTextColor(ContextCompat.getColor(this, R.color.error))
                    }
                }
            }
        }
    }
}
