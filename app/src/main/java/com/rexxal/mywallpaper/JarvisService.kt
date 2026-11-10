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
        const val CH = "jarvis_quiet"
        fun running() = instance != null
    }
    private var busy = false


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            try { nm.deleteNotificationChannel("jarvis") } catch (e: Exception) { }
            nm.createNotificationChannel(NotificationChannel(CH, "Background helper", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("MyWallpaper").setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) else startForeground(7, n)
        val f = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screen, f)
        Ui.prefs(this).edit().putBoolean("jv_running", true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_SCREEN_ON) faceCheck() }
    }

    /** Face check when the lock screen turns on (only if switched on and your face is added). */
    private fun faceCheck() {
        val p = Ui.prefs(this)
        if (!p.getBoolean("jv_face_on", false) || busy) return
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return
        busy = true
        p.edit().putString("jv_ev_kind", "check").putLong("jv_ev_time", System.currentTimeMillis()).apply()
        JarvisCamera.capture(this, "check") { f ->
            if (f == null) { busy = false; p.edit().putString("jv_res_kind", "noface").putInt("jv_res_score", 0).putLong("jv_res_time", System.currentTimeMillis()).apply(); return@capture }
            Thread {
                val bmp = try { ImageLoader.load(this, android.net.Uri.fromFile(f), 720, 960, 2_000_000L) } catch (e: Throwable) { null }
                val res = if (bmp == null) FaceMatcher.Result.NoFace else FaceMatcher.check(this, bmp)
                val kind = when (res) { is FaceMatcher.Result.Done -> if (res.owner) "owner" else "stranger"; else -> "noface" }
                val sc = (res as? FaceMatcher.Result.Done)?.score ?: 0
                // keep strangers (and unclear photos) in the log; your own photo is deleted
                val dest = java.io.File(f.parentFile, f.name.replace("_check", "_$kind"))
                if (kind == "owner") android.os.Handler(mainLooper).postDelayed({ f.delete() }, 20_000) else f.renameTo(dest)
                val path = if (kind == "owner") f.path else dest.path
                p.edit().putString("jv_photo", path).putLong("jv_photo_time", System.currentTimeMillis())
                    .putString("jv_res_kind", kind).putInt("jv_res_score", sc).putLong("jv_res_time", System.currentTimeMillis()).apply()
                busy = false
                if (queued > 0) { queued--; android.os.Handler(mainLooper).post { event("intruder") } }
            }.start()
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(screen) } catch (e: Exception) { }
        instance = null
        Ui.prefs(this).edit().putBoolean("jv_running", false).apply()
        super.onDestroy()
    }

    private var queued = 0

    /** Only wrong PIN/pattern attempts take a photo: every attempt, none skipped. */
    fun event(kind: String) {
        if (kind != "intruder") return
        val p = Ui.prefs(this)
        if (busy) { queued++; return }          // another wrong attempt while the camera is busy: take it right after
        busy = true
        p.edit().putString("jv_ev_kind", kind).putLong("jv_ev_time", System.currentTimeMillis()).apply()   // the wallpaper starts its animation
        JarvisCamera.capture(this, kind) { f ->
            busy = false
            if (f != null) p.edit().putString("jv_photo", f.path).putLong("jv_photo_time", System.currentTimeMillis()).apply()
            if (queued > 0) { queued--; event("intruder") }
        }
    }
}

/** Gets told by Android when someone enters a wrong PIN, pattern or password. */
class JarvisAdmin : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        JarvisService.instance?.event("intruder")
    }
}
