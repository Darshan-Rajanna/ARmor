package com.example.armor

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val extras = sbn.notification.extras

        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        if (text == null) return

        val message = when {
            packageName.contains("com.whatsapp", ignoreCase = true) -> {
                "WhatsApp: ${title ?: ""}: $text"
            }

            packageName.contains("com.truecaller", ignoreCase = true) ||
                    packageName.contains("com.android.dialer", ignoreCase = true) ||
                    packageName.contains("com.google.android.dialer", ignoreCase = true) -> {
                val callerInfo = title ?: "Unknown Number"
                "Incoming Call: $callerInfo"
            }

            packageName.contains("maps", ignoreCase = true) ||
                    packageName.contains("navigation", ignoreCase = true) -> {
                "NAV: $text"
            }

            else -> {
                // Fallback for any other app
                val app = getAppName(packageName)
                "$app: ${title ?: ""} $text"
            }
        }

        sendNotificationToActivity(message)
    }

    private fun sendNotificationToActivity(message: String) {
        val intent = Intent("com.example.armor.NOTIFICATION_EVENT")
        intent.putExtra("data", message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName // fallback to raw package name
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
