package com.cafetera

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object CoffeeNotifications {
    const val CHANNEL_ID = "cafetera_status"
    private const val NOTIFICATION_ID = 41
    private const val OPEN_REQUEST_CODE = 41
    private const val MODE_ALLOW_ALWAYS = 3

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = context.getString(R.string.notification_channel_description)
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, body: String) {
        createChannel(context)
        val openApp = openIntent(context, body)
        val pendingIntent = launchPendingIntent(context, openApp)
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_cafe_button)
                .setColor(ContextCompat.getColor(context, R.color.coffee_brown))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
        openNow(context, pendingIntent, openApp)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun openIntent(context: Context, body: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_COFFEE_READY, true)
            putExtra(MainActivity.EXTRA_COFFEE_MESSAGE, body)
        }
    }

    private fun launchPendingIntent(context: Context, openApp: Intent): PendingIntent {
        PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (Build.VERSION.SDK_INT < 34) {
            return PendingIntent.getActivity(context, OPEN_REQUEST_CODE, openApp, flags)
        }
        val options = ActivityOptions.makeBasic()
        options.pendingIntentCreatorBackgroundActivityStartMode = backgroundStartMode()
        return PendingIntent.getActivity(context, OPEN_REQUEST_CODE, openApp, flags, options.toBundle())
    }

    private fun backgroundStartMode(): Int {
        return if (Build.VERSION.SDK_INT >= 36) {
            MODE_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
    }

    private fun openNow(context: Context, pendingIntent: PendingIntent, openApp: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                options.pendingIntentBackgroundActivityStartMode = backgroundStartMode()
                pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                context.startActivity(openApp)
            }
        } catch (_: Exception) {
            try {
                context.startActivity(openApp)
            } catch (_: Exception) {
            }
        }
    }
}
