package com.cafetera

import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object CoffeeAlerts {
    var listener: ((String) -> Unit)? = null
    var waiting: Boolean = false
}

class CoffeeMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["message"]
            ?: message.data["body"]
            ?: return
        CoffeeReady.mark(this)
        val appContext = applicationContext
        Handler(Looper.getMainLooper()).post {
            val onScreen = CoffeeAlerts.listener != null
            if (onScreen) {
                CoffeeAlerts.listener?.invoke(body)
                CoffeeNotifications.cancel(appContext)
                Handler(Looper.getMainLooper()).postDelayed({
                    CoffeeNotifications.cancel(appContext)
                }, 600)
            } else {
                CoffeeNotifications.show(appContext, title, body)
            }
        }
    }
}
