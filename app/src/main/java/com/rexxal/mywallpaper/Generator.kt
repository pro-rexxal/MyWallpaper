package com.rexxal.mywallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Built-in wallpaper pack: every style draws a new design for every seed. */
object Generator {

    val STYLES = listOf(
        "Aurora", "Mountains", "Waves", "Bokeh", "Mesh",
        "Low-poly", "Synthwave", "Rings", "Starry night", "Dunes"
    )

    // Each palette: dark, mid, bright, accent
    val PALETTE_NAMES = listOf("Random", "Violet", "Ocean", "Sunset", "Forest", "Candy", "Ember", "Ice", "Mono")
    private val PALETTES = listOf(
        intArrayOf(0xFF0D0B1E, 0xFF3A1C71, 0xFF7C5CFF, 0xFFFF6EC7),
        intArrayOf(0xFF031926, 0xFF0B4F6C, 0xFF20A4F3, 0xFF7FFFD4),
        intArrayOf(0xFF1A0B2E, 0xFF7B2D5B, 0xFFFF7E5F, 0xFFFEB47B),
        intArrayOf(0xFF07130D, 0xFF1F4D2B, 0xFF57A773, 0xFFD9ED92),
        intArrayOf(0xFF2B0F3A, 0xFFE4508F, 0xFFFFB3C6, 0xFF8EECF5),
        intArrayOf(0xFF140404, 0xFF6A0D0D, 0xFFFF4D00, 0xFFFFC300),
        intArrayOf(0xFF0A1220, 0xFF2E4A6B, 0xFFA9D6E5, 0xFFFFFFFF),
        intArrayOf(0xFF0B0B0C, 0xFF3A3A40, 0xFF9A9AA5, 0xFFF2F2F2)
    ).map { arr -> IntArray(arr.size) { arr[it].toInt() } }

    fun render(style: Int, seed: Long, palette: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = Random(seed * 7919 + style)
        val pal = if (palette <= 0) PALETTES[r.nextInt(PALETTES.size)] else PALETTES[(palette - 1) % PALETTES.size]
        val wf = w.toFloat()
        val hf = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (style) {
            0 -> aurora(c, wf, hf, r, pal, p)
            1 -> mountains(c, wf, hf, r, pal, p)
            2 -> waves(c, wf, hf, r, pal, p)
            3 -> bokeh(c, wf, hf, r, pal, p)
            4 -> mesh(c, wf, hf, r, pal, p)
            5 -> lowPoly(c, wf, hf, r, pal, p)
            6 -> synthwave(c, wf, hf, r, pal, p)
            7 -> rings(c, wf, hf, r, pal, p)
            8 -> starry(c, wf, hf, r, pal, p)
            else -> dunes(c, wf, hf, r, pal, p)
        }
        return bmp
    }

    // ---------- helpers ----------

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun vertical(c: Canvas, w: Float, h: Float, top: Int, bottom: Int, p: Paint) {
        p.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    private fun glow(c: Canvas, x: Float, y: Float, rad: Float, col: Int, a: Int, w: Float, h: Float, p: Paint) {
        p.shader = RadialGradient(x, y, rad, intArrayOf(alpha(col, a), alpha(col, 0)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    /** Wavy ridge line across the screen, filled down to the bottom. */
    private fun ridge(baseY: Float, amp: Float, w: Float, h: Float, r: Random, sharp: Boolean): Path {
        val path = Path()
        path.moveTo(0f, h)
        val f1 = 1f + r.nextFloat() * 2f
        val f2 = 3f + r.nextFloat() * 5f
        val ph1 = r.nextFloat() * 6.28f
        val ph2 = r.nextFloat() * 6.28f
        val steps = 60
        for (i in 0..steps) {
            val x = w * i / steps
            val t = i.toFloat() / steps * 6.28f
            var y = baseY + amp * (0.65f * sin(t * f1 + ph1) + 0.35f * sin(t * f2 + ph2))
            if (sharp) y += amp * 0.25f * (r.nextFloat() - 0.5f)
            path.lineTo(x, y)
        }
        path.lineTo(w, h)
        path.close()
        return path
    }

    // ---------- styles ----------

    private fun aurora(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        vertical(c, w, h, pal[0], mix(pal[0], pal[1], 0.4f), p)
        for (k in 0 until 4) {
            val y = h * (0.15f + r.nextFloat() * 0.45f)
            val col = if (k % 2 == 0) pal[2] else pal[3]
            val band = Path()
            val amp = h * (0.04f + r.nextFloat() * 0.06f)
            val f = 1f + r.nextFloat() * 2f
            val ph = r.nextFloat() * 6.28f
            val thick = h * (0.08f + r.nextFloat() * 0.12f)
            band.moveTo(0f, y)
            for (i in 0..40) {
                val x = w * i / 40
                band.lineTo(x, y + amp * sin(i / 40f * 6.28f * f + ph))
            }
            for (i in 40 downTo 0) {
                val x = w * i / 40
                band.lineTo(x, y + thick + amp * sin(i / 40f * 6.28f * f + ph))
            }
            band.close()
            p.shader = LinearGradient(0f, y - amp, 0f, y + thick + amp,
                intArrayOf(alpha(col, 0), alpha(col, 120), alpha(col, 0)), null, Shader.TileMode.CLAMP)
            c.drawPath(band, p)
        }
        p.shader = null
        stars(c, w, h * 0.6f, r, 60, p)
        p.color = pal[0]
        c.drawPath(ridge(h * 0.86f, h * 0.03f, w, h, r, true), p)
    }

    private fun mountains(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        vertical(c, w, h, pal[3], pal[2], p)
        val sx = w * (0.2f + r.nextFloat() * 0.6f)
        val sy = h * (0.25f + r.nextFloat() * 0.15f)
        glow(c, sx, sy, w * 0.7f, Color.WHITE, 90, w, h, p)
        p.color = alpha(Color.WHITE, 220)
        c.drawCircle(sx, sy, w * 0.08f, p)
        val layers = 5
        for (i in 0 until layers) {
            val t = i / (layers - 1f)
            p.color = mix(mix(pal[2], pal[1], 0.5f), pal[0], t)
            c.drawPath(ridge(h * (0.42f + t * 0.4f), h * (0.08f - t * 0.04f), w, h, r, true), p)
        }
    }

    private fun waves(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        vertical(c, w, h, pal[2], pal[3], p)
        val layers = 7
        for (i in 0 until layers) {
            val t = i / (layers - 1f)
            p.color = mix(pal[2], pal[0], t)
            p.setShadowLayer(w * 0.03f, 0f, -w * 0.01f, 0x55000000)
            c.drawPath(ridge(h * (0.3f + t * 0.6f), h * 0.035f, w, h, r, false), p)
        }
        p.clearShadowLayer()
    }

    private fun bokeh(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        vertical(c, w, h, pal[0], pal[1], p)
        repeat(45) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            val rad = w * (0.03f + r.nextFloat() * 0.14f)
            val col = if (r.nextBoolean()) pal[2] else pal[3]
            p.shader = RadialGradient(x, y, rad,
                intArrayOf(alpha(col, 40 + r.nextInt(90)), alpha(col, 30), alpha(col, 0)),
                floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, rad, p)
        }
        p.shader = null
    }

    private fun mesh(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        c.drawColor(pal[0])
        for (k in 0 until 6) {
            val col = pal[1 + r.nextInt(3)]
            glow(c, r.nextFloat() * w, r.nextFloat() * h, w * (0.6f + r.nextFloat() * 0.6f), col, 170, w, h, p)
        }
        // fine grain for a premium look
        val g = Paint()
        repeat((w * h / 900).toInt()) {
            g.color = if (r.nextBoolean()) 0x0CFFFFFF else 0x0C000000
            c.drawPoint(r.nextFloat() * w, r.nextFloat() * h, g)
        }
    }

    private fun lowPoly(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        val cols = 7
        val rows = 15
        val cw = w / cols
        val ch = h / rows
        val xs = Array(rows + 1) { FloatArray(cols + 1) }
        val ys = Array(rows + 1) { FloatArray(cols + 1) }
        for (j in 0..rows) for (i in 0..cols) {
            val edgeX = i == 0 || i == cols
            val edgeY = j == 0 || j == rows
            xs[j][i] = i * cw + if (edgeX) 0f else (r.nextFloat() - 0.5f) * cw * 0.8f
            ys[j][i] = j * ch + if (edgeY) 0f else (r.nextFloat() - 0.5f) * ch * 0.8f
        }
        p.style = Paint.Style.FILL
        val angle = r.nextFloat() * 6.28f
        for (j in 0 until rows) for (i in 0 until cols) {
            for (tri in 0..1) {
                val path = Path()
                val ax: Float; val ay: Float; val bx: Float; val by: Float; val cx: Float; val cy: Float
                if (tri == 0) {
                    ax = xs[j][i]; ay = ys[j][i]; bx = xs[j][i + 1]; by = ys[j][i + 1]; cx = xs[j + 1][i]; cy = ys[j + 1][i]
                } else {
                    ax = xs[j][i + 1]; ay = ys[j][i + 1]; bx = xs[j + 1][i + 1]; by = ys[j + 1][i + 1]; cx = xs[j + 1][i]; cy = ys[j + 1][i]
                }
                path.moveTo(ax, ay); path.lineTo(bx, by); path.lineTo(cx, cy); path.close()
                val mx = (ax + bx + cx) / 3f / w
                val my = (ay + by + cy) / 3f / h
                val t = (mx * cos(angle) + my * sin(angle) + 1f) / 2f
                val base = if (t < 0.5f) mix(pal[0], pal[1], t * 2) else mix(pal[1], pal[2], (t - 0.5f) * 2)
                p.color = mix(base, if (r.nextBoolean()) Color.WHITE else Color.BLACK, r.nextFloat() * 0.12f)
                c.drawPath(path, p)
            }
        }
    }

    private fun synthwave(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        val horizon = h * (0.55f + r.nextFloat() * 0.1f)
        vertical(c, w, horizon, pal[0], pal[1], p)
        stars(c, w, horizon * 0.7f, r, 70, p)
        val sunR = w * (0.28f + r.nextFloat() * 0.1f)
        val sx = w / 2
        val sy = horizon - sunR * 0.35f
        glow(c, sx, sy, sunR * 2.2f, pal[3], 110, w, horizon, p)
        p.shader = LinearGradient(0f, sy - sunR, 0f, sy + sunR, pal[3], pal[2], Shader.TileMode.CLAMP)
        c.save()
        c.clipRect(0f, 0f, w, horizon)
        c.drawCircle(sx, sy, sunR, p)
        p.shader = null
        // stripes cut through the sun
        p.color = pal[1]
        var y = sy
        var gap = sunR * 0.04f
        while (y < sy + sunR) {
            c.drawRect(0f, y, w, y + gap, p)
            y += gap * 3f
            gap *= 1.25f
        }
        c.restore()
        // grid floor
        p.color = pal[0]
        c.drawRect(0f, horizon, w, h, p)
        p.color = alpha(pal[3], 200)
        p.strokeWidth = w * 0.004f
        var k = 1f
        while (horizon + k * k * h * 0.006f < h) {
            val gy = horizon + k * k * h * 0.006f
            c.drawLine(0f, gy, w, gy, p)
            k += 1f
        }
        for (i in -12..12) {
            c.drawLine(w / 2 + i * w * 0.02f, horizon, w / 2 + i * w * 0.25f, h, p)
        }
        glow(c, w / 2, horizon, w * 0.6f, pal[3], 60, w, h, p)
    }

    private fun rings(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        c.drawColor(pal[0])
        val cx = w * (0.2f + r.nextFloat() * 0.6f)
        val cy = h * (0.3f + r.nextFloat() * 0.4f)
        val maxR = max(w, h) * 1.1f
        val n = 14 + r.nextInt(10)
        for (i in n downTo 1) {
            val t = i / n.toFloat()
            p.color = if (i % 2 == 0) mix(pal[1], pal[0], t) else mix(pal[2], pal[1], t)
            c.drawCircle(cx + (r.nextFloat() - 0.5f) * w * 0.02f, cy, maxR * t, p)
        }
        glow(c, cx, cy, w * 0.5f, pal[3], 90, w, h, p)
    }

    private fun starry(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        vertical(c, w, h, 0xFF02030A.toInt(), pal[1], p)
        // milky way band
        c.save()
        c.rotate(-25f + r.nextFloat() * 50f, w / 2, h * 0.4f)
        p.shader = LinearGradient(0f, h * 0.3f, 0f, h * 0.5f,
            intArrayOf(alpha(pal[2], 0), alpha(pal[2], 70), alpha(pal[2], 0)), null, Shader.TileMode.CLAMP)
        c.drawRect(-w, h * 0.3f, w * 2, h * 0.5f, p)
        p.shader = null
        c.restore()
        stars(c, w, h, r, 260, p)
        val mx = w * (0.2f + r.nextFloat() * 0.6f)
        val my = h * (0.12f + r.nextFloat() * 0.15f)
        glow(c, mx, my, w * 0.35f, Color.WHITE, 60, w, h, p)
        p.color = 0xFFF4F1E8.toInt()
        c.drawCircle(mx, my, w * 0.06f, p)
        p.color = 0xFF02030A.toInt()
        c.drawPath(ridge(h * 0.88f, h * 0.025f, w, h, r, true), p)
    }

    private fun dunes(c: Canvas, w: Float, h: Float, r: Random, pal: IntArray, p: Paint) {
        vertical(c, w, h, pal[3], mix(pal[3], pal[2], 0.6f), p)
        val sx = w * (0.15f + r.nextFloat() * 0.7f)
        glow(c, sx, h * 0.3f, w * 0.8f, Color.WHITE, 80, w, h, p)
        val layers = 6
        for (i in 0 until layers) {
            val t = i / (layers - 1f)
            val top = h * (0.4f + t * 0.45f)
            p.shader = LinearGradient(0f, top - h * 0.05f, 0f, top + h * 0.15f,
                mix(pal[2], pal[1], t), mix(pal[1], pal[0], t), Shader.TileMode.CLAMP)
            c.drawPath(ridge(top, h * 0.05f, w, h, r, false), p)
        }
        p.shader = null
    }

    private fun stars(c: Canvas, w: Float, h: Float, r: Random, n: Int, p: Paint) {
        p.shader = null
        repeat(n) {
            p.color = alpha(Color.WHITE, 60 + r.nextInt(190))
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, w * (0.0008f + r.nextFloat() * 0.0025f), p)
        }
    }

    @Suppress("unused")
    private val TAU = (2 * PI).toFloat()
}
