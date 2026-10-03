package com.cafetera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cafetera.databinding.ActivityMainBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var config: MqttConfig? = null
    private var forgetReadyOnNextStart = false
    private var cup = CupMark.NONE
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            binding.status.text = getString(R.string.notifications_denied)
            binding.status.setTextColor(ContextCompat.getColor(this, R.color.error))
        }
        loadPushToken()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (intent.getBooleanExtra(EXTRA_COFFEE_READY, false)) {
            CoffeeReady.mark(this)
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        } else {
            CoffeeReady.clear(this)
            ensureCanOpenWhenClosed()
        }
        CoffeeNotifications.createChannel(this)
        try {
            val loaded = MqttConfig.load(this)
            config = loaded
            if (loaded.debug) {
                binding.configSummary.visibility = android.view.View.VISIBLE
                binding.configSummary.text = listOf(
                    getString(R.string.broker_line, loaded.broker),
                    getString(R.string.topic_line, loaded.topic),
                    getString(R.string.payload_line, loaded.payload),
                ).joinToString("\n")
            }
        } catch (error: Exception) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            binding.status.text = getString(R.string.config_error, detail)
            binding.status.setTextColor(ContextCompat.getColor(this, R.color.error))
            binding.brewButton.isEnabled = false
        }
        preparePush()
        showReadyFromIntent(intent)

        binding.brewButton.setOnClickListener {
            val current = config ?: return@setOnClickListener
            clearReadyRequest()
            showCup(CupMark.NOTE)
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
                        if (cup != CupMark.TICKET) {
                            showCup(CupMark.CLOCK)
                            binding.status.text = getString(R.string.status_ok)
                            binding.status.setTextColor(ContextCompat.getColor(this, R.color.ok))
                        }
                    } else {
                        showCup(CupMark.NONE)
                        binding.status.text = getString(R.string.status_error, detail ?: "")
                        binding.status.setTextColor(ContextCompat.getColor(this, R.color.error))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_COFFEE_READY, false)) {
            CoffeeReady.mark(this)
            showReadyFromIntent(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        CoffeeAlerts.listener = { body, readyAt ->
            if (!isDestroyed) {
                showCup(CupMark.TICKET)
                binding.status.text = getString(R.string.coffee_ready, body)
                binding.status.setTextColor(ContextCompat.getColor(this, R.color.ok))
                showReadyTime(readyAt)
            }
        }
        if (intent.getBooleanExtra(EXTRA_COFFEE_READY, false)) {
            showReadyFromIntent(intent)
        } else if (forgetReadyOnNextStart) {
            showCup(CupMark.NONE)
            binding.status.text = getString(R.string.status_idle)
            binding.status.setTextColor(ContextCompat.getColor(this, R.color.ink))
        } else {
            showCup(CupMark.NONE)
        }
        forgetReadyOnNextStart = false
    }

    override fun onStop() {
        CoffeeAlerts.listener = null
        CoffeeAlerts.waiting = false
        val showingReady = binding.readyMark.visibility == android.view.View.VISIBLE
        if (showingReady || intent.getBooleanExtra(EXTRA_COFFEE_READY, false)) {
            intent.removeExtra(EXTRA_COFFEE_READY)
            intent.removeExtra(EXTRA_COFFEE_MESSAGE)
            intent.removeExtra(EXTRA_COFFEE_READY_AT)
            CoffeeReady.clear(this)
            forgetReadyOnNextStart = true
        }
        super.onStop()
    }

    companion object {
        const val EXTRA_COFFEE_READY = "coffee_ready"
        const val EXTRA_COFFEE_MESSAGE = "coffee_message"
        const val EXTRA_COFFEE_READY_AT = "coffee_ready_at"
        private const val OVERLAY_PROMPTED = "overlay_prompted"
    }

    private fun showReadyFromIntent(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_COFFEE_READY, false)) return
        showCup(CupMark.TICKET)
        showReadyTime(intent.getStringExtra(EXTRA_COFFEE_READY_AT))
        val message = intent.getStringExtra(EXTRA_COFFEE_MESSAGE)?.takeIf { it.isNotBlank() } ?: return
        binding.status.text = getString(R.string.coffee_ready, message)
        binding.status.setTextColor(ContextCompat.getColor(this, R.color.ok))
    }

    private fun ensureCanOpenWhenClosed() {
        if (Settings.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("cafetera", MODE_PRIVATE)
        if (prefs.getBoolean(OVERLAY_PROMPTED, false)) return
        prefs.edit().putBoolean(OVERLAY_PROMPTED, true).apply()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun clearReadyRequest() {
        CoffeeReady.clear(this)
        intent.removeExtra(EXTRA_COFFEE_READY)
        intent.removeExtra(EXTRA_COFFEE_MESSAGE)
        intent.removeExtra(EXTRA_COFFEE_READY_AT)
        showReadyTime(null)
        forgetReadyOnNextStart = false
    }

    private fun showReadyTime(raw: String?) {
        val label = CoffeeTime.label(raw)
        if (label == null) {
            binding.readyTime.visibility = android.view.View.GONE
            return
        }
        binding.readyTime.text = getString(R.string.coffee_ready_time, label)
        binding.readyTime.visibility = android.view.View.VISIBLE
    }

    private fun showCup(mark: CupMark) {
        cup = mark
        CoffeeAlerts.waiting = mark == CupMark.CLOCK
        if (mark != CupMark.TICKET) {
            binding.readyTime.visibility = android.view.View.GONE
        }
        if (mark == CupMark.NONE) {
            binding.readyMark.visibility = android.view.View.GONE
            return
        }
        val icon = when (mark) {
            CupMark.NOTE -> R.drawable.ic_cup_note to R.string.cup_note
            CupMark.CLOCK -> R.drawable.ic_cup_clock to R.string.cup_wait
            CupMark.TICKET -> R.drawable.ic_ready_check to R.string.coffee_ready_mark
            CupMark.NONE -> return
        }
        binding.readyMark.setImageResource(icon.first)
        binding.readyMark.contentDescription = getString(icon.second)
        binding.readyMark.visibility = android.view.View.VISIBLE
    }

    private enum class CupMark {
        NONE,
        NOTE,
        CLOCK,
        TICKET,
    }

    private fun preparePush() {
        if (config?.debug != true) return
        binding.pushToken.visibility = android.view.View.VISIBLE
        if (FirebaseApp.getApps(this).isEmpty()) {
            binding.pushToken.text = getString(R.string.push_not_configured)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        loadPushToken()
    }

    private fun loadPushToken() {
        if (config?.debug != true || FirebaseApp.getApps(this).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (isDestroyed) return@addOnCompleteListener
            binding.pushToken.text = if (task.isSuccessful) {
                getString(R.string.push_token_line, task.result)
            } else {
                getString(R.string.push_token_error)
            }
        }
    }
}
