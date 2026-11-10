package com.rexxal.mywallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

/** Shows the live preview in the phone's shape. Drag to move, pinch to zoom. */
class CropView(context: Context) : View(context) {

    var aspect = 9f / 20f                 // screen width / height
    var onCropChanged: (() -> Unit)? = null
    var preview: Bitmap? = null
    val crop = RectF()                    // area of the source image that becomes the wallpaper

    private var source: Bitmap? = null
    private val frame = RectF()
    private val clip = Path()
    private val density = resources.displayMetrics.density
    private val corner get() = 16f * density

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1E25.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x40FFFFFF
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A8D98.toInt()
        textSize = 15f * density
        textAlign = Paint.Align.CENTER
    }

    private var lastX = 0f
    private var lastY = 0f

    val frameW: Int get() = frame.width().toInt()
    val frameH: Int get() = frame.height().toInt()

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoom(d.scaleFactor, d.focusX, d.focusY)
                return true
            }
        })

    fun setImage(bmp: Bitmap) {
        source = bmp
        resetCrop()
    }

    fun resetCrop() {
        val s = source ?: return
        val w = min(s.width.toFloat(), s.height * aspect)
        val h = w / aspect
        crop.set((s.width - w) / 2f, (s.height - h) / 2f, (s.width + w) / 2f, (s.height + h) / 2f)
        notifyChanged()
    }

    private fun notifyChanged() {
        onCropChanged?.invoke()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 4f * density
        val aw = w - 2 * pad
        val ah = h - 2 * pad
        var fw = aw
        var fh = fw / aspect
        if (fh > ah) {
            fh = ah
            fw = fh * aspect
        }
        frame.set((w - fw) / 2f, (h - fh) / 2f, (w + fw) / 2f, (h + fh) / 2f)
        clip.reset()
        clip.addRoundRect(frame, corner, corner, Path.Direction.CW)
        notifyChanged()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clip)
        val p = preview
        if (p != null && source != null) {
            canvas.drawBitmap(p, null, frame, bitmapPaint)
        } else {
            canvas.drawRect(frame, emptyPaint)
            canvas.drawText("Tap to pick a photo", frame.centerX(), frame.centerY(), textPaint)
        }
        canvas.restore()
        canvas.drawRoundRect(frame, corner, corner, borderPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (source == null) {
            if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            return true
        }
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val i = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(i); lastY = e.getY(i)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1 && !scaleDetector.isInProgress) pan(e.x - lastX, e.y - lastY)
                lastX = e.x; lastY = e.y
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pan(dx: Float, dy: Float) {
        if (frame.width() <= 0f) return
        val k = crop.width() / frame.width()
        crop.offset(-dx * k, -dy * k)
        clamp()
        notifyChanged()
    }

    private fun zoom(f: Float, fx: Float, fy: Float) {
        val s = source ?: return
        if (frame.width() <= 0f) return
        val k = crop.width() / frame.width()
        val sx = crop.left + (fx - frame.left) * k
        val sy = crop.top + (fy - frame.top) * k
        val maxW = min(s.width.toFloat(), s.height * aspect)
        val newW = (crop.width() / f).coerceIn(maxW / 8f, maxW)
        val newH = newW / aspect
        val rx = (sx - crop.left) / crop.width()
        val ry = (sy - crop.top) / crop.height()
        val left = sx - rx * newW
        val top = sy - ry * newH
        crop.set(left, top, left + newW, top + newH)
        clamp()
        notifyChanged()
    }

    private fun clamp() {
        val s = source ?: return
        if (crop.left < 0f) crop.offset(-crop.left, 0f)
        if (crop.top < 0f) crop.offset(0f, -crop.top)
        if (crop.right > s.width) crop.offset(s.width - crop.right, 0f)
        if (crop.bottom > s.height) crop.offset(0f, s.height - crop.bottom)
    }
}
