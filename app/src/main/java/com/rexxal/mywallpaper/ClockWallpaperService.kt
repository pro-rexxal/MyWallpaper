package com.rexxal.mywallpaper

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.abs

/** The live wallpaper itself: background + customizable clock and date. */
class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private val prefs = Ui.prefs(this@ClockWallpaperService)
        private var cfg = LiveConfig.load(prefs)
        private var photo: Bitmap? = null
        private var photoVer = -1L
        private var visible = false
        private val robot = Robot()
        private var sw = 1
        private var sh = 1
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private val drawRunnable = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(this)
            reload()
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            handler.removeCallbacks(drawRunnable)
            super.onDestroy()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                robot.onReturn(System.currentTimeMillis())
                drawFrame()
            } else handler.removeCallbacks(drawRunnable)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            sw = width.coerceAtLeast(1)
            sh = height.coerceAtLeast(1)
            drawFrame()
        }

        override fun onTouchEvent(e: MotionEvent) {
            if (!cfg.robot) return
            val now = System.currentTimeMillis()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.x - downX) + abs(e.y - downY) > sw * 0.05f) dragging = true
                    if (dragging) robot.onDrag(e.x / sw, e.y / sh, now)
                }
                MotionEvent.ACTION_UP -> if (!dragging) {
                    robot.onTap(e.x / sw, e.y / sh, now, cfg.robotSize / 100f, sw.toFloat() / sh)
                }
            }
            if (visible) drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            reload()
            drawFrame()
        }

        private fun reload() {
            cfg = LiveConfig.load(prefs)
            val ver = prefs.getLong("live_bg_ver", 0L)
            if (cfg.bgMode == 0 && (photo == null || ver != photoVer)) {
                photoVer = ver
                val (sw, _) = Ui.screenSize(this@ClockWallpaperService)
                photo = LiveRenderer.loadPhoto(this@ClockWallpaperService, sw)
            } else if (cfg.bgMode != 0) {
                photo = null
            }
        }

        private fun drawFrame() {
            handler.removeCallbacks(drawRunnable)
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    LiveRenderer.draw(canvas, canvas.width, canvas.height, System.currentTimeMillis(), cfg, photo, robot)
                }
            } catch (e: Exception) {
                // surface not ready; try again next frame
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                    }
                }
            }
            if (visible) {
                handler.postDelayed(drawRunnable, LiveRenderer.nextDelay(cfg, System.currentTimeMillis()))
            }
        }
    }
}
