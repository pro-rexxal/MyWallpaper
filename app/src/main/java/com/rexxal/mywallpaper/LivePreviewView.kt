package com.rexxal.mywallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Phone-shaped live preview of the Live wallpaper. You can play with the robot right here. */
class LivePreviewView(context: Context) : View(context) {
    var aspect = 9f / 20f
    var cfg = LiveConfig()
    var photo: Bitmap? = null
    val world = RoomWorld()
    val touch = RoomTouch(world)
    private val battery = BatteryReader(context)

    private val density = resources.displayMetrics.density
    private val frame = RectF()
    private val clip = Path()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x40FFFFFF
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 4f * density
        var fh = h - 2 * pad
        var fw = fh * aspect
        if (fw > w - 2 * pad) { fw = w - 2 * pad; fh = fw / aspect }
        frame.set((w - fw) / 2f, (h - fh) / 2f, (w + fw) / 2f, (h + fh) / 2f)
        clip.reset()
        clip.addRoundRect(frame, 16 * density, 16 * density, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (frame.width() <= 0f) return
        val now = System.currentTimeMillis()
        if (battery.poll(now)) world.setPower(battery.level, battery.charging, now)
        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(frame.left, frame.top)
        LiveRenderer.draw(canvas, frame.width().toInt(), frame.height().toInt(), now, cfg, photo, world)
        canvas.restore()
        canvas.drawRoundRect(frame, 16 * density, 16 * density, border)
        if (isShown) {
            if (cfg.animated) postInvalidateOnAnimation()
            else postInvalidateDelayed(LiveRenderer.nextDelay(cfg, now).coerceAtMost(1000L))
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        val used = touch.onTouch(e, e.x - frame.left, e.y - frame.top, frame.width(), frame.height(), cfg)
        invalidate()
        return used
    }
}
