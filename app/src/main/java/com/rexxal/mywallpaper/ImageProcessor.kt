package com.rexxal.mywallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object ImageProcessor {

    enum class Filter(val label: String) {
        NORMAL("Normal"), VIVID("Vivid"), BW("B&W"), NOIR("Noir"),
        SEPIA("Sepia"), WARM("Warm"), COOL("Cool"), FADE("Fade")
    }

    data class Settings(
        var brightness: Float = 0f,   // -0.5 .. 0.5
        var contrast: Float = 1f,     // 0.5 .. 1.5
        var saturation: Float = 1f,   // 0 .. 2
        var warmth: Float = 0f,       // -1 .. 1
        var blur: Int = 0,            // 0 .. 100
        var dim: Float = 0f,          // 0 .. 0.85
        var vignette: Float = 0f,     // 0 .. 1
        var filter: Filter = Filter.NORMAL
    )

    private fun cm(vararg v: Float) = ColorMatrix(v)

    private fun contrastMatrix(c: Float, offset: Float = 0f): ColorMatrix {
        val t = (1f - c) * 128f + offset
        return cm(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    }

    private fun filterMatrix(f: Filter): ColorMatrix = when (f) {
        Filter.NORMAL -> ColorMatrix()
        Filter.VIVID -> ColorMatrix().apply { setSaturation(1.45f); postConcat(contrastMatrix(1.1f)) }
        Filter.BW -> ColorMatrix().apply { setSaturation(0f) }
        Filter.NOIR -> ColorMatrix().apply { setSaturation(0f); postConcat(contrastMatrix(1.45f, -10f)) }
        Filter.SEPIA -> cm(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        Filter.WARM -> cm(
            1.1f, 0f, 0f, 0f, 12f,
            0f, 1.02f, 0f, 0f, 4f,
            0f, 0f, 0.88f, 0f, -8f,
            0f, 0f, 0f, 1f, 0f
        )
        Filter.COOL -> cm(
            0.9f, 0f, 0f, 0f, -6f,
            0f, 1f, 0f, 0f, 2f,
            0f, 0f, 1.12f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        )
        Filter.FADE -> ColorMatrix().apply { setSaturation(0.75f); postConcat(contrastMatrix(0.8f, 22f)) }
    }

    fun colorMatrix(s: Settings): ColorMatrix {
        val m = filterMatrix(s.filter)
        m.postConcat(ColorMatrix().apply { setSaturation(s.saturation) })
        m.postConcat(contrastMatrix(s.contrast, s.brightness * 255f))
        val w = s.warmth * 40f
        m.postConcat(
            cm(
                1f, 0f, 0f, 0f, w,
                0f, 1f, 0f, 0f, w * 0.2f,
                0f, 0f, 1f, 0f, -w,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return m
    }

    /**
     * Renders the cropped part of [src] at outW x outH with all effects applied.
     * hq = true does a careful multi-step downscale (used for the real wallpaper).
     */
    fun render(src: Bitmap, crop: RectF, outW: Int, outH: Int, s: Settings, hq: Boolean): Bitmap {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val dst = RectF(0f, 0f, outW.toFloat(), outH.toFloat())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix(s))
        }

        val srcRect = Rect(
            floor(crop.left).toInt().coerceAtLeast(0),
            floor(crop.top).toInt().coerceAtLeast(0),
            ceil(crop.right).toInt().coerceAtMost(src.width),
            ceil(crop.bottom).toInt().coerceAtMost(src.height)
        )
        if (srcRect.width() <= 0 || srcRect.height() <= 0) return out

        var base = src
        var baseRect: Rect? = srcRect
        if (hq && srcRect.width() > outW * 2) {
            var cur = Bitmap.createBitmap(src, srcRect.left, srcRect.top, srcRect.width(), srcRect.height())
            while (cur.width > outW * 2) {
                val next = Bitmap.createScaledBitmap(cur, cur.width / 2, max(1, cur.height / 2), true)
                if (cur !== src) cur.recycle()
                cur = next
            }
            base = cur
            baseRect = null
        }

        if (s.blur > 0) {
            val sw = max(8, outW / 4)
            val sh = max(8, outH / 4)
            val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            Canvas(small).drawBitmap(base, baseRect, RectF(0f, 0f, sw.toFloat(), sh.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            boxBlur(small, max(1, (s.blur / 100f * sw * 0.10f).roundToInt()))
            canvas.drawBitmap(small, null, dst, paint)
            small.recycle()
        } else {
            canvas.drawBitmap(base, baseRect, dst, paint)
        }
        if (base !== src) base.recycle()

        if (s.vignette > 0f) {
            val cx = outW / 2f
            val cy = outH / 2f
            val a = (s.vignette * 230).toInt()
            val vp = Paint().apply {
                shader = RadialGradient(
                    cx, cy, hypot(cx, cy),
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(a, 0, 0, 0)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(dst, vp)
        }
        if (s.dim > 0f) {
            canvas.drawColor(Color.argb((s.dim * 255).toInt(), 0, 0, 0))
        }
        return out
    }

    /** Fast blur: 3 box-blur passes (looks close to a Gaussian blur). */
    private fun boxBlur(bmp: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            blurPass(px, tmp, w, h, radius)  // blur rows, write transposed
            blurPass(tmp, px, h, w, radius)  // blur columns, transpose back
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var ta = 0; var tr = 0; var tg = 0; var tb = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                ta += p ushr 24; tr += (p shr 16) and 0xff; tg += (p shr 8) and 0xff; tb += p and 0xff
            }
            for (x in 0 until w) {
                dst[x * h + y] = ((ta / div) shl 24) or ((tr / div) shl 16) or ((tg / div) shl 8) or (tb / div)
                val pOut = src[row + (x - r).coerceIn(0, w - 1)]
                val pIn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                ta += (pIn ushr 24) - (pOut ushr 24)
                tr += ((pIn shr 16) and 0xff) - ((pOut shr 16) and 0xff)
                tg += ((pIn shr 8) and 0xff) - ((pOut shr 8) and 0xff)
                tb += (pIn and 0xff) - (pOut and 0xff)
            }
        }
    }
}
