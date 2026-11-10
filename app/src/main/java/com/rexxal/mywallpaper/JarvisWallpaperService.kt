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
        }

        override fun onDestroy() { prefs.unregisterOnSharedPreferenceChangeListener(this); handler.removeCallbacks(drawRunnable); super.onDestroy() }

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
                else -> if (key?.startsWith("jv_") == true) JarvisSettings.apply(prefs, jr)
            }
            if (visible) drawFrame()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) { val pk = pending; if (pk != null) jr.trigger(pk, System.currentTimeMillis()) else jr.clear(); pending = null; drawFrame() } else handler.removeCallbacks(drawRunnable)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { super.onSurfaceChanged(holder, format, width, height); drawFrame() }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { visible = false; handler.removeCallbacks(drawRunnable); super.onSurfaceDestroyed(holder) }

        private fun drawFrame() {
            handler.removeCallbacks(drawRunnable)
            val now = System.currentTimeMillis()
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
        jr.msgWake = p.getString("jv_msg_wake", null) ?: jr.msgWake
        jr.msgIntruder = p.getString("jv_msg_intr", null) ?: jr.msgIntruder
    }
}
