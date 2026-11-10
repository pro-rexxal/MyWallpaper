package com.rexxal.mywallpaper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.UserHandle

/** Always-on helper that lets Jarvis use the front camera from the lock screen. */
class JarvisService : Service() {
    companion object {
        @Volatile var instance: JarvisService? = null
        private const val CH = "jarvis"
        fun running() = instance != null
    }
    private var busy = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_SCREEN_ON) event("wake") }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Jarvis", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("Jarvis is watching 👀").setContentText("Front camera ready for the lock screen")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) else startForeground(7, n)
        val f = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screen, f)
        Ui.prefs(this).edit().putBoolean("jv_running", true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(screen) } catch (e: Exception) { }
        instance = null
        Ui.prefs(this).edit().putBoolean("jv_running", false).apply()
        super.onDestroy()
    }

    /** "wake" = lock screen turned on (rate-limited), "intruder" = wrong PIN. */
    fun event(kind: String) {
        val p = Ui.prefs(this); val now = System.currentTimeMillis()
        if (kind == "wake") {
            if (!p.getBoolean("jv_on_wake", true)) return
            val km = getSystemService(android.app.KeyguardManager::class.java)
            if (!km.isKeyguardLocked) return
            if (now - p.getLong("jv_last_wake", 0L) < p.getInt("jv_gap_min", 10) * 60_000L) return
            p.edit().putLong("jv_last_wake", now).apply()
        } else if (!p.getBoolean("jv_on_intruder", true)) return
        if (busy) return
        busy = true
        p.edit().putString("jv_ev_kind", kind).putLong("jv_ev_time", now).apply()   // the wallpaper starts its animation
        JarvisCamera.capture(this, kind) { f ->
            busy = false
            if (f != null) p.edit().putString("jv_photo", f.path).putLong("jv_photo_time", System.currentTimeMillis()).apply()
        }
    }
}

/** Gets told by Android when someone enters a wrong PIN, pattern or password. */
class JarvisAdmin : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        JarvisService.instance?.event("intruder")
    }
}
