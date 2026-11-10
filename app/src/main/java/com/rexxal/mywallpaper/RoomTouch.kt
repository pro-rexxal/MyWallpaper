package com.rexxal.mywallpaper

import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sign

/** Turns raw touches into robot gestures: tap, hold (pet), drag (pick up), swipe up (flip), swipe sideways (wind). */
class RoomTouch(private val world: RoomWorld) {
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f; private var downY = 0f; private var downT = 0L
    private var onBot = false; private var dragging = false; private var long = false
    var onLightsChanged: ((Boolean) -> Unit)? = null

    /** x, y in pixels inside an area of w × h. Returns true when the touch was used. */
    fun onTouch(e: MotionEvent, x: Float, y: Float, w: Float, h: Float, cfg: LiveConfig): Boolean {
        if (!cfg.robot || w <= 0f || h <= 0f) return false
        val nx = x / w; val ny = y / h + (if (cfg.bgMode == 4) cfg.roomShift / 100f else 0f); val now = System.currentTimeMillis()
        val size = cfg.robotSize / 100f; val aspect = w / h
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; downT = now; dragging = false; long = false
                onBot = world.botHit(nx, ny, size, aspect)
                handler.removeCallbacksAndMessages(null)
                if (onBot) handler.postDelayed({ if (!dragging) { long = true; world.longPress(nx, ny, System.currentTimeMillis(), size, aspect) } }, 450)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(x - downX) + abs(y - downY) > w * 0.04f) {
                    dragging = true; handler.removeCallbacksAndMessages(null)
                    if (onBot && !long) world.pickUp(nx, ny, now)
                }
                if (dragging && world.bot.carried) world.dragTo(nx, ny)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacksAndMessages(null)
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) { world.release(); return true }
                val dx = x - downX; val dy = y - downY; val dt = now - downT
                when {
                    world.bot.carried || long -> world.release()
                    dragging -> {
                        if (dt < 500 && -dy > h * 0.12f && abs(dy) > abs(dx)) world.swipeUp(now)
                        else if (dt < 500 && abs(dx) > w * 0.25f && abs(dx) > abs(dy)) world.swipeSide(sign(dx), now)
                    }
                    else -> {
                        val before = world.lightsOn
                        world.tap(nx, ny, now, size, aspect, cfg.bgMode == 4)
                        if (world.lightsOn != before) onLightsChanged?.invoke(world.lightsOn)
                    }
                }
            }
        }
        return true
    }
}

/** Reads the phone's battery level and whether it's plugged in (cheap; called about once a second). */
class BatteryReader(private val c: Context) {
    private var last = 0L
    var level = 1f; private set
    var charging = false; private set

    fun poll(now: Long): Boolean {
        if (now - last < 800L) return false
        last = now
        try {
            // the sticky battery broadcast is the most reliable source on every phone brand
            val i = c.applicationContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (lv >= 0 && sc > 0) level = (lv.toFloat() / sc).coerceIn(0.01f, 1f)
                val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = plugged != 0 || status == BatteryManager.STATUS_CHARGING || status == BatteryManager.STATUS_FULL
            }
        } catch (e: Exception) { }
        return true
    }
}
