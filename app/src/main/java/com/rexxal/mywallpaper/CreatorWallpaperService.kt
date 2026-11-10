package com.rexxal.mywallpaper

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Creator live wallpaper. Plays each layer's in/out animation when the screen wakes,
 * when you unlock (lock → home) and when you lock again.
 */
class CreatorWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CreatorEngine()

    inner class CreatorEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val ctx = this@CreatorWallpaperService
        private val handler = Handler(Looper.getMainLooper())
        private val prefs = Ui.prefs(ctx)
        private var design = Design.load(prefs)
        private val state = CreatorState()
        private val cache = CreatorRenderer.BgCache()
        private var visible = false
        private val drawRunnable = Runnable { drawFrame() }
        private val keyguard = ctx.getSystemService(KeyguardManager::class.java)

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val now = System.currentTimeMillis()
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> state.go(CreatorState.OFF, design, now, instant = true)
                    Intent.ACTION_SCREEN_ON -> state.go(if (isLocked()) CreatorState.LOCK else CreatorState.HOME, design, now)
                    Intent.ACTION_USER_PRESENT -> state.go(CreatorState.HOME, design, now)
                }
                drawFrame()
            }
        }

        private fun isLocked() = try { keyguard.isKeyguardLocked } catch (e: Exception) { false }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(this)
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
                else ctx.registerReceiver(screenReceiver, f)
            } catch (e: Exception) { }
            reloadBg()
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            try { ctx.unregisterReceiver(screenReceiver) } catch (e: Exception) { }
            handler.removeCallbacks(drawRunnable)
            super.onDestroy()
        }

        private fun reloadBg() {
            val (sw, _) = Ui.screenSize(ctx)
            CreatorRenderer.loadBg(ctx, cache, prefs.getLong("creator_bg_ver", 0L), sw)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                val now = System.currentTimeMillis()
                val target = if (isPreview()) CreatorState.LOCK else if (isLocked()) CreatorState.LOCK else CreatorState.HOME
                if (state.stage != target) state.go(target, design, now)
                drawFrame()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            if (key == Design.KEY) {
                design = Design.load(prefs)
                state.go(state.stage, design, System.currentTimeMillis(), instant = true)
            }
            if (key == "creator_bg_ver") reloadBg()
            if (visible) drawFrame()
        }

        private fun drawFrame() {
            handler.removeCallbacks(drawRunnable)
            val holder = surfaceHolder
            var canvas: Canvas? = null
            val now = System.currentTimeMillis()
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    CreatorRenderer.draw(canvas, canvas.width.toFloat(), canvas.height.toFloat(), now, design, state, cache, null)
                }
            } catch (e: Exception) {
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
            }
            if (visible) handler.postDelayed(drawRunnable, CreatorRenderer.nextDelay(design, state, now))
        }
    }
}
