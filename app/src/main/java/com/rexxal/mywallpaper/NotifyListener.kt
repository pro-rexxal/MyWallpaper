package com.rexxal.mywallpaper

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lets the robot react when a notification arrives. Only the app's name is used; nothing is stored or sent. */
class NotifyListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing || sbn.packageName == packageName) return
        val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { "" }
        Ui.prefs(this).edit().putString("notif_app", name).putLong("notif_time", System.currentTimeMillis()).apply()
    }

    companion object {
        fun enabled(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.contains(ComponentName(c, NotifyListener::class.java).flattenToString())
        }
    }
}
