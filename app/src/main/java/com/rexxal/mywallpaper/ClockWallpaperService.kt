package com.rexxal.mywallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

/** The Live wallpaper: robot room (or other backgrounds) + customizable clock and date. */
class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val ctx = this@ClockWallpaperService
        private val handler = Handler(Looper.getMainLooper())
        private val prefs = Ui.prefs(ctx)
        private var cfg = LiveConfig.load(prefs)
        private var photo: Bitmap? = null
        private var photoVer = -1L
        private var visible = false
        private val world = RoomWorld()
        private val touch = RoomTouch(world)
        private val battery = BatteryReader(ctx)
        private var sw = 1
        private var sh = 1
        private var hardware = true
        private var lastFrameNs = 0L
        private val drawRunnable = Runnable { drawFrame() }


        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val now = System.currentTimeMillis()
                when (i.action) { Intent.ACTION_SCREEN_ON -> world.onScreenOn(now); Intent.ACTION_USER_PRESENT -> world.onUnlock(now) }
                if (visible) drawFrame()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
            try { if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED) else ctx.registerReceiver(screenReceiver, f) } catch (e: Exception) { }
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(this)
            touch.onLightsChanged = { on -> cfg.lightsOn = on; prefs.edit().putBoolean("lv_lights", on).apply() }
            reload()
            WeatherFetcher.refresh(ctx)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            try { ctx.unregisterReceiver(screenReceiver) } catch (e: Exception) { }
            stopFrames()
            super.onDestroy()
        }

        private fun stopFrames() {
            handler.removeCallbacks(drawRunnable)
        }



        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                val now = System.currentTimeMillis()
                
                if (cfg.winMode == 0 && cfg.weatherSel == 0) WeatherFetcher.refresh(ctx)
                drawFrame()
            } else stopFrames()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            sw = width.coerceAtLeast(1); sh = height.coerceAtLeast(1)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stopFrames()
            super.onSurfaceDestroyed(holder)
        }

        override fun onTouchEvent(e: MotionEvent) {
            if (touch.onTouch(e, e.x, e.y, sw.toFloat(), sh.toFloat(), cfg) && visible) drawFrame()
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            if (key == "notif_time") { world.onNotification(prefs.getString("notif_app", "") ?: "", System.currentTimeMillis()); if (visible) drawFrame(); return }
            reload()
            if (visible) drawFrame()
        }

        private fun reload() {
            cfg = LiveConfig.load(prefs)
            val ver = prefs.getLong("live_bg_ver", 0L)
            if (cfg.bgMode == 0 && (photo == null || ver != photoVer)) {
                photoVer = ver
                val (w, _) = Ui.screenSize(ctx)
                photo = LiveRenderer.loadPhoto(ctx, w)
            } else if (cfg.bgMode != 0) photo = null
        }

        private fun drawFrame() {
            stopFrames()
            val now = System.currentTimeMillis()
            if (battery.poll(now)) world.setPower(battery.level, battery.charging, now)
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (hardware) {
                    try { holder.lockHardwareCanvas() } catch (e: Exception) { hardware = false; holder.lockCanvas() }
                } else holder.lockCanvas()
                if (canvas != null) LiveRenderer.draw(canvas, canvas.width, canvas.height, now, cfg, photo, world)
            } catch (e: Exception) {
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
            }
            if (visible) {
                val delay = LiveRenderer.nextDelay(cfg, System.currentTimeMillis())
                // steady timer: ~30fps for the room, ~60fps otherwise, minus the time this frame took
                val target = if (cfg.bgMode == 4) 33L else 16L
                val spent = System.currentTimeMillis() - now
                handler.postDelayed(drawRunnable, if (delay <= 40L) (target - spent).coerceAtLeast(4L) else delay)
            }
        }
    }
}
