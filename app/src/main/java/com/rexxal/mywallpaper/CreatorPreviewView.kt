package com.rexxal.mywallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Phone-shaped live preview of the Creator design. Tap a layer to select it, drag to move it. */
class CreatorPreviewView(context: Context) : View(context) {
    var aspect = 9f / 20f
    var design = Design()
    val state = CreatorState()
    val cache = CreatorRenderer.BgCache()
    var selected = -1
    var onSelect: ((Int) -> Unit)? = null
    var onMoved: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val frame = RectF()
    private val clip = Path()
    private val bounds = mutableListOf<RectF>()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = 0x40FFFFFF }
    private val selP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = 0xCC7C5CFF.toInt() }
    private val stageP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 11f * density; textAlign = Paint.Align.CENTER }
    private var dragging = -1
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var moved = false

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
        while (bounds.size < design.items.size) bounds += RectF()
        val now = System.currentTimeMillis()
        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(frame.left, frame.top)
        CreatorRenderer.draw(canvas, frame.width(), frame.height(), now, design, state, cache, bounds)
        bounds.getOrNull(selected)?.let { canvas.drawRoundRect(it, 8 * density, 8 * density, selP) }
        val label = when (state.stage) { CreatorState.LOCK -> "LOCK SCREEN"; CreatorState.HOME -> "HOME SCREEN"; else -> "SCREEN OFF" }
        canvas.drawText(label, frame.width() / 2, frame.height() - 10 * density, stageP)
        canvas.restore()
        canvas.drawRoundRect(frame, 16 * density, 16 * density, border)
        if (isShown) {
            if (CreatorRenderer.needsFrames(design, state, now)) postInvalidateOnAnimation()
            else postInvalidateDelayed(1000L - now % 1000L)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val lx = e.x - frame.left
        val ly = e.y - frame.top
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = -1
                for (i in design.items.indices.reversed()) {
                    val el = design.items[i]
                    val visible = (state.stage == CreatorState.LOCK && el.showLock) || (state.stage == CreatorState.HOME && el.showHome)
                    if (visible && bounds.getOrNull(i)?.contains(lx, ly) == true) { dragging = i; break }
                }
                downX = e.x; downY = e.y; moved = false
                if (dragging >= 0) {
                    startX = design.items[dragging].x; startY = design.items[dragging].y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragging >= 0) {
                if (Math.abs(e.x - downX) + Math.abs(e.y - downY) > 6 * density) moved = true
                if (moved) {
                    val el = design.items[dragging]
                    el.x = (startX + (e.x - downX) / frame.width()).coerceIn(0.02f, 0.98f)
                    el.y = (startY + (e.y - downY) / frame.height()).coerceIn(0.02f, 0.98f)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging >= 0) {
                    selected = dragging
                    onSelect?.invoke(dragging)
                    if (moved) onMoved?.invoke()
                }
                dragging = -1
                invalidate()
            }
        }
        return true
    }
}
