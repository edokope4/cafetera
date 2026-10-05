package com.cafetera

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

    fun show(context: Context, title: String, body: String, readyAt: String) {
        createChannel(context)
        val openApp = openIntent(context, body, readyAt)
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
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun openIntent(context: Context, body: String, readyAt: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_COFFEE_READY, true)
            putExtra(MainActivity.EXTRA_COFFEE_MESSAGE, body)
            putExtra(MainActivity.EXTRA_COFFEE_READY_AT, readyAt)
        }
    }

    private fun launchPendingIntent(context: Context, openApp: Intent): PendingIntent {
        return PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
