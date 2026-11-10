package com.rexxal.mywallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Phone-shaped live preview of the live wallpaper. */
class LivePreviewView(context: Context) : View(context) {
    var aspect = 9f / 20f
    var cfg = LiveConfig()
    var photo: Bitmap? = null
    val robot = Robot()
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

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
        if (fw > w - 2 * pad) {
            fw = w - 2 * pad
            fh = fw / aspect
        }
        frame.set((w - fw) / 2f, (h - fh) / 2f, (w + fw) / 2f, (h + fh) / 2f)
        clip.reset()
        clip.addRoundRect(frame, 16 * density, 16 * density, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (frame.width() <= 0f) return
        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(frame.left, frame.top)
        val now = System.currentTimeMillis()
        LiveRenderer.draw(canvas, frame.width().toInt(), frame.height().toInt(), now, cfg, photo, robot)
        canvas.restore()
        canvas.drawRoundRect(frame, 16 * density, 16 * density, border)
        if (isShown && cfg.animated) postInvalidateOnAnimation()
        else if (isShown) postInvalidateDelayed(LiveRenderer.nextDelay(cfg, now).coerceAtMost(1000L))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!cfg.robot || frame.width() <= 0f) return false
        val nx = (e.x - frame.left) / frame.width()
        val ny = (e.y - frame.top) / frame.height()
        val now = System.currentTimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.x - downX) + abs(e.y - downY) > frame.width() * 0.05f) dragging = true
                if (dragging) robot.onDrag(nx, ny, now)
            }
            MotionEvent.ACTION_UP -> if (!dragging) {
                robot.onTap(nx, ny, now, cfg.robotSize / 100f, frame.width() / frame.height())
            }
        }
        invalidate()
        return true
    }
}
