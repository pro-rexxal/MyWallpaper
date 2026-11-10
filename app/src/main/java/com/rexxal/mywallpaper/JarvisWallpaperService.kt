package com.rexxal.mywallpaper

import android.content.SharedPreferences
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File

/** The Jarvis live wallpaper. */
class JarvisWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = JarvisEngine()

    inner class JarvisEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val ctx = this@JarvisWallpaperService
        private val prefs = Ui.prefs(ctx)
        private val handler = Handler(Looper.getMainLooper())
        private val jr = JarvisRenderer()
        private var visible = false
        private var pending: String? = null
        private var hardware = true
        private val drawRunnable = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
            JarvisSettings.apply(prefs, jr)
            setTouchEventsEnabled(true)
            val f = android.content.IntentFilter().apply { addAction(android.content.Intent.ACTION_SCREEN_ON); addAction(android.content.Intent.ACTION_SCREEN_OFF); addAction(android.content.Intent.ACTION_USER_PRESENT) }
            if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(screenRx, f, android.content.Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(screenRx, f)
        }

        // someone wakes the locked phone -> Jarvis leans in and watches the keypad
        private val screenRx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val now = System.currentTimeMillis()
                when (i.action) {
                    android.content.Intent.ACTION_SCREEN_ON -> { val km = ctx.getSystemService(android.app.KeyguardManager::class.java); if (km.isKeyguardLocked) jr.attention(now, true) }
                    else -> jr.attention(now, false)
                }
            }
        }

        private var lastLockCheck = 0L; private var wasLocked = false
        private var downX = 0f; private var downY = 0f; private var downT = 0L
        override fun onTouchEvent(e: android.view.MotionEvent) {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; downT = System.currentTimeMillis() }
                android.view.MotionEvent.ACTION_UP -> {
                    val now = System.currentTimeMillis(); val fr = surfaceHolder.surfaceFrame
                    if (now - downT < 350 && Math.abs(e.x - downX) + Math.abs(e.y - downY) < fr.width() * .04f && fr.width() > 0)
                        jr.tap(e.x / fr.width(), e.y / fr.height(), now, fr.width().toFloat(), fr.height().toFloat())
                }
            }
        }

        override fun onDestroy() { try { ctx.unregisterReceiver(screenRx) } catch (e: Exception) { }; prefs.unregisterOnSharedPreferenceChangeListener(this); handler.removeCallbacks(drawRunnable); super.onDestroy() }

        private fun loadPhoto(path: String?) {
            if (path == null) return
            Thread {
                val bmp = try { ImageLoader.load(ctx, Uri.fromFile(File(path)), 480, 640, 1_500_000L) } catch (e: Throwable) { null }
                handler.post { if (bmp != null) jr.photo = bmp }
            }.start()
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            when (key) {
                "jv_ev_time" -> { val kind = prefs.getString("jv_ev_kind", "wake") ?: "wake"; if (visible) jr.trigger(kind, System.currentTimeMillis()) else pending = kind }
                "jv_photo_time" -> if (jr.busy(System.currentTimeMillis())) loadPhoto(prefs.getString("jv_photo", null))
                "jv_res_time" -> jr.resolve(prefs.getString("jv_res_kind", "noface") ?: "noface", prefs.getInt("jv_res_score", 0))
                else -> if (key?.startsWith("jv_") == true || key?.startsWith("wx_") == true) JarvisSettings.apply(prefs, jr)
            }
            if (visible) drawFrame()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) { WeatherFetcher.refresh(ctx); val pk = pending; if (pk != null) jr.trigger(pk, System.currentTimeMillis()) else jr.clear(); pending = null; drawFrame() } else handler.removeCallbacks(drawRunnable)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { super.onSurfaceChanged(holder, format, width, height); drawFrame() }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { visible = false; handler.removeCallbacks(drawRunnable); super.onSurfaceDestroyed(holder) }

        private fun drawFrame() {
            handler.removeCallbacks(drawRunnable)
            val now = System.currentTimeMillis()
            // MIUI doesn't always deliver SCREEN_ON to wallpapers: poll the lock state instead
            if (now - lastLockCheck > 500) {
                lastLockCheck = now
                val locked = try { ctx.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked } catch (e: Exception) { false }
                if (locked) jr.attention(now, true, keep = true) else if (wasLocked) jr.attention(now, false)
                wasLocked = locked
            }
            var c: Canvas? = null
            try {
                c = if (hardware) { try { surfaceHolder.lockHardwareCanvas() } catch (e: Exception) { hardware = false; surfaceHolder.lockCanvas() } } else surfaceHolder.lockCanvas()
                if (c != null) jr.draw(c, c.width.toFloat(), c.height.toFloat(), now)
            } catch (e: Exception) {
            } finally { if (c != null) try { surfaceHolder.unlockCanvasAndPost(c) } catch (e: Exception) { } }
            if (visible) handler.postDelayed(drawRunnable, (33L - (System.currentTimeMillis() - now)).coerceAtLeast(4L))
        }
    }
}

object JarvisSettings {
    fun apply(p: SharedPreferences, jr: JarvisRenderer) {
        jr.accent = JarvisRenderer.ACCENTS[p.getInt("jv_accent", 0).coerceIn(0, JarvisRenderer.ACCENTS.size - 1)]
        jr.body = p.getInt("jv_body", 0xFFE9ECF5.toInt())
        jr.light = p.getBoolean("jv_light", false)
        jr.weather = p.getString("wx_now", "Clear") ?: "Clear"
        if (p.contains("wx_lat")) { jr.lat = p.getFloat("wx_lat", 22f); jr.lon = p.getFloat("wx_lon", jr.lon) }
        jr.msgWake = p.getString("jv_msg_wake", null) ?: jr.msgWake
        jr.msgIntruder = p.getString("jv_msg_intr", null) ?: jr.msgIntruder
        jr.msgOwner = p.getString("jv_msg_owner", null) ?: jr.msgOwner
        jr.msgStranger = p.getString("jv_msg_stranger", null) ?: jr.msgStranger
        jr.msgNoFace = p.getString("jv_msg_noface", null) ?: jr.msgNoFace
    }
}
