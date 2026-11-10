package com.rexxal.mywallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Animation state of one layer: p = 0 hidden … 1 fully shown. */
class ElAnim {
    var from = 0f
    var to = 0f
    var start = 0L
    var dur = 700
    var dirIn = true

    fun progress(now: Long): Float {
        if (now < start) return from
        val t = ((now - start).toFloat() / dur).coerceIn(0f, 1f)
        return from + (to - from) * t
    }

    fun running(now: Long) = now < start + dur && from != to
}

/** Shared wake / lock / home state for the wallpaper and the in-app preview. */
class CreatorState {
    companion object {
        const val OFF = 0
        const val LOCK = 1
        const val HOME = 2
    }

    var stage = OFF
    private val anims = mutableListOf<ElAnim>()
    private val home = ElAnim().apply { dur = 650 }

    fun anim(i: Int): ElAnim {
        while (anims.size <= i) anims += ElAnim()
        return anims[i]
    }

    fun homeT(now: Long): Float = CreatorRenderer.easeInOut(home.progress(now))

    fun go(stage: Int, d: Design, now: Long, instant: Boolean = false) {
        this.stage = stage
        d.items.forEachIndexed { i, el ->
            val show = when (stage) {
                LOCK -> el.showLock
                HOME -> el.showHome
                else -> false
            }
            val a = anim(i)
            val target = if (show) 1f else 0f
            if (instant) {
                a.from = target; a.to = target; a.start = 0L
            } else if (a.to != target || a.progress(now) != target) {
                a.from = a.progress(now)
                a.to = target
                a.dur = el.duration.coerceAtLeast(50)
                a.start = now + if (show) el.delay.toLong() else 0L
                a.dirIn = show
            }
        }
        val ht = if (stage == HOME) 1f else 0f
        if (instant) {
            home.from = ht; home.to = ht; home.start = 0L
        } else if (home.to != ht) {
            home.from = home.progress(now); home.to = ht; home.start = now
        }
    }

    /** Re-plays the in-animation of one layer (for the editor's preview button). */
    fun replay(i: Int, d: Design, now: Long) {
        val el = d.items.getOrNull(i) ?: return
        val a = anim(i)
        a.from = 0f; a.to = 1f; a.dur = el.duration.coerceAtLeast(50); a.start = now + el.delay; a.dirIn = true
    }

    fun busy(now: Long): Boolean = anims.any { it.running(now) || now < it.start } || home.running(now)
}

object CreatorRenderer {

    val ANIMS = listOf(
        "None", "Fade", "Slide up", "Slide down", "Slide left", "Slide right", "Zoom in", "Zoom out",
        "Pop", "Flip", "Spin", "Typewriter", "Letter cascade", "Glitch", "Wave"
    )
    val IDLES = listOf("None", "Pulse", "Float", "Shimmer", "Flicker", "Glow breathe", "Wave", "Rainbow")
    val FONT_LABELS = listOf(
        "Default", "Thin", "Light", "Medium", "Black", "Condensed", "Cond. light",
        "Serif", "Mono", "Serif mono", "Casual", "Cursive", "Small caps"
    )
    private val FAMILIES = listOf(
        "sans-serif", "sans-serif-thin", "sans-serif-light", "sans-serif-medium", "sans-serif-black",
        "sans-serif-condensed", "sans-serif-condensed-light", "serif", "monospace", "serif-monospace",
        "casual", "cursive", "sans-serif-smallcaps"
    )
    val DATE_PATTERNS = listOf(
        "EEEE, d MMMM", "EEE, d MMM", "d MMMM yyyy", "dd/MM/yyyy", "MMMM d", "EEEE", "EEE dd.MM", "dd MMM yyyy"
    )

    private const val A_NONE = 0
    private const val A_FADE = 1
    private const val A_UP = 2
    private const val A_DOWN = 3
    private const val A_LEFT = 4
    private const val A_RIGHT = 5
    private const val A_ZOOM_IN = 6
    private const val A_ZOOM_OUT = 7
    private const val A_POP = 8
    private const val A_FLIP = 9
    private const val A_SPIN = 10
    private const val A_TYPE = 11
    private const val A_CASCADE = 12
    private const val A_GLITCH = 13
    private const val A_WAVE = 14

    private const val I_PULSE = 1
    private const val I_FLOAT = 2
    private const val I_SHIMMER = 3
    private const val I_FLICKER = 4
    private const val I_BREATHE = 5
    private const val I_WAVE = 6
    private const val I_RAINBOW = 7

    fun easeOut(t: Float) = 1f - (1f - t).pow(3)
    fun easeIn(t: Float) = t * t * t
    fun easeInOut(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    private fun easeBack(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1; return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2) }

    // ---------------- fonts ----------------
    private val tfCache = HashMap<String, Typeface>()

    fun typeface(el: TextEl): Typeface {
        val key = "${el.font}|${el.bold}|${el.italic}|${el.customFont}"
        tfCache[key]?.let { return it }
        val style = when {
            el.bold && el.italic -> Typeface.BOLD_ITALIC
            el.bold -> Typeface.BOLD
            el.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val path = el.customFont
        val tf = try {
            if (path != null && File(path).exists()) Typeface.create(Typeface.createFromFile(path), style)
            else Typeface.create(FAMILIES[el.font.coerceIn(0, FAMILIES.size - 1)], style)
        } catch (e: Exception) {
            Typeface.create(Typeface.DEFAULT, style)
        }
        tfCache[key] = tf
        return tf
    }

    // ---------------- text content ----------------
    fun lines(el: TextEl, now: Long): List<String> {
        val d = Date(now)
        return when (el.kind) {
            TextEl.KIND_TIME -> {
                val h = if (el.use24) (if (el.leadZero) "HH" else "H") else (if (el.leadZero) "hh" else "h")
                val sec = if (el.seconds) ":ss" else ""
                val ap = if (!el.use24 && el.ampm) " a" else ""
                val f = { p: String -> SimpleDateFormat(p, Locale.getDefault()).format(d) }
                if (el.stacked) listOf(f(h), f("mm$sec") + (if (ap.isEmpty()) "" else f(ap)))
                else listOf(f("$h:mm$sec$ap"))
            }
            TextEl.KIND_DATE -> listOf(
                try { SimpleDateFormat(el.datePattern, Locale.getDefault()).format(d) } catch (e: Exception) { "Check date format" }
            )
            else -> el.text.split("\n").ifEmpty { listOf("") }
        }
    }

    // ---------------- background ----------------
    class BgCache {
        var photo: Bitmap? = null
        var blurred: Bitmap? = null
        var ver = -2L
    }

    fun loadBg(c: Context, cache: BgCache, ver: Long, maxW: Int) {
        if (cache.ver == ver) return
        cache.ver = ver
        val f = Design.photoFile(c)
        cache.photo = null
        cache.blurred = null
        if (!f.exists()) return
        try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var s = 1
            while (b.outWidth / (s * 2) >= maxW) s *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return
            cache.photo = bmp
            val q = Bitmap.createScaledBitmap(bmp, max(4, bmp.width / 4), max(4, bmp.height / 4), true)
            cache.blurred = Bitmap.createScaledBitmap(q, max(2, q.width / 4), max(2, q.height / 4), true)
            if (q !== bmp) q.recycle()
        } catch (e: Throwable) {
            cache.photo = null
        }
    }

    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fillBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun cover(c: Canvas, b: Bitmap, w: Float, h: Float, p: Paint) {
        val sc = max(w / b.width, h / b.height)
        val dw = b.width * sc
        val dh = b.height * sc
        rect.set((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
        c.drawBitmap(b, null, rect, p)
    }

    fun drawBg(c: Canvas, w: Float, h: Float, bg: CreatorBg, cache: BgCache, homeT: Float) {
        c.drawColor(Color.BLACK)
        c.save()
        val sc = 1f + bg.zoomHome / 100f * homeT
        c.scale(sc, sc, w / 2, h / 2)
        val photo = cache.photo
        if (bg.type == 0 && photo != null) {
            bgPaint.alpha = 255
            cover(c, photo, w, h, bgPaint)
            val bl = cache.blurred
            if (bg.blurHome && bl != null && homeT > 0f) {
                bgPaint.alpha = (255 * homeT).toInt()
                cover(c, bl, w, h, bgPaint)
                bgPaint.alpha = 255
            }
        } else if (bg.type == 2) {
            c.drawColor(bg.c1)
        } else {
            val a = Math.toRadians(bg.angle.toDouble())
            val r = max(w, h) / 2
            val cx = w / 2; val cy = h / 2
            fillBg.shader = LinearGradient(
                cx - cos(a).toFloat() * r, cy - sin(a).toFloat() * r, cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r,
                bg.c1, bg.c2, Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w, h, fillBg)
            fillBg.shader = null
        }
        c.restore()
        val dim = bg.dimLock + (bg.dimHome - bg.dimLock) * homeT
        if (dim > 0f) c.drawColor(Color.argb((dim / 100f * 255).toInt().coerceIn(0, 255), 0, 0, 0))
    }

    // ---------------- text drawing ----------------
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val depthP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val camera = Camera()
    private val matrix = Matrix()
    private val hsv = FloatArray(3)

    private fun withAlpha(col: Int, a: Float) = (col and 0x00FFFFFF) or ((Color.alpha(col) * a.coerceIn(0f, 1f)).toInt() shl 24)
    private fun shade(col: Int, f: Float) = Color.argb(Color.alpha(col), (Color.red(col) * f).toInt(), (Color.green(col) * f).toInt(), (Color.blue(col) * f).toInt())

    private fun setup(p: Paint, tf: Typeface, size: Float, el: TextEl) {
        p.typeface = tf
        p.textSize = size
        p.letterSpacing = el.spacing / 100f
        p.textAlign = Paint.Align.LEFT
    }

    /** Draws one string with every effect. x,y = baseline start. */
    private fun styled(c: Canvas, s: String, x: Float, y: Float, el: TextEl, size: Float, alpha: Float, now: Long, blockW: Float) {
        if (alpha <= 0.003f || s.isEmpty()) return
        val tf = typeface(el)
        val t = now / 1000f
        var glowMul = 1f
        if (el.idle == I_BREATHE) glowMul = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 2.2f))
        var mainColor = el.color
        if (el.idle == I_RAINBOW) {
            Color.colorToHSV(el.color, hsv)
            hsv[0] = (t * 60f) % 360f
            if (hsv[1] < 0.4f) hsv[1] = 0.65f
            if (hsv[2] < 0.6f) hsv[2] = 1f
            mainColor = Color.HSVToColor(Color.alpha(el.color), hsv)
        }

        // glow
        val glow = if (el.idle == I_BREATHE && el.glow == 0) 50 else el.glow
        if (glow > 0) {
            setup(glowP, tf, size, el)
            glowP.style = Paint.Style.FILL
            glowP.color = withAlpha(el.glowColor, alpha * 0.9f)
            glowP.setShadowLayer(size * 0.28f * glow / 100f * glowMul + 1f, 0f, 0f, withAlpha(el.glowColor, alpha))
            c.drawText(s, x, y, glowP)
            if (glow > 50) c.drawText(s, x, y, glowP)
            glowP.clearShadowLayer()
        }
        // 3D extrude
        if (el.depth > 0) {
            setup(depthP, tf, size, el)
            val a = Math.toRadians(el.depthAngle.toDouble())
            val dx = cos(a).toFloat() * size * 0.012f
            val dy = sin(a).toFloat() * size * 0.012f
            for (k in el.depth downTo 1) {
                depthP.color = withAlpha(shade(el.depthColor, 1f - 0.45f * k / el.depth), alpha)
                if (k == el.depth && el.shadow) depthP.setShadowLayer(el.shadowBlur * size / 100f + 0.5f, 0f, el.shadowDy * size / 100f, withAlpha(el.shadowColor, alpha))
                else depthP.clearShadowLayer()
                c.drawText(s, x + dx * k, y + dy * k, depthP)
            }
            depthP.clearShadowLayer()
        }
        // outline
        if (el.outline > 0) {
            setup(strokeP, tf, size, el)
            strokeP.strokeWidth = size * el.outline * 0.014f
            strokeP.color = withAlpha(el.outlineColor, alpha)
            if (el.shadow && el.depth == 0) strokeP.setShadowLayer(el.shadowBlur * size / 100f + 0.5f, 0f, el.shadowDy * size / 100f, withAlpha(el.shadowColor, alpha)) else strokeP.clearShadowLayer()
            c.drawText(s, x, y, strokeP)
            strokeP.clearShadowLayer()
        }
        // fill
        setup(fillP, tf, size, el)
        fillP.color = mainColor
        fillP.shader = when {
            el.idle == I_SHIMMER -> {
                val span = max(blockW, size) * 1.6f
                val pos = -span + ((t * 0.45f) % 1.6f) * span * 1.4f
                val base = if (el.gradient) el.color2 else mainColor
                LinearGradient(pos, 0f, pos + span * 0.35f, 0f, intArrayOf(base, Color.WHITE, base), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            }
            el.gradient -> LinearGradient(0f, y - size * 0.8f, 0f, y + size * 0.1f, mainColor, el.color2, Shader.TileMode.CLAMP)
            else -> null
        }
        fillP.alpha = (Color.alpha(mainColor) * alpha).toInt().coerceIn(0, 255)
        if (el.shadow && el.depth == 0 && el.outline == 0) fillP.setShadowLayer(el.shadowBlur * size / 100f + 0.5f, 0f, el.shadowDy * size / 100f, withAlpha(el.shadowColor, alpha))
        else fillP.clearShadowLayer()
        c.drawText(s, x, y, fillP)
        fillP.shader = null
        fillP.clearShadowLayer()
    }

    /**
     * Draws one layer. [p] 0..1 from its ElAnim, [dirIn] true while it is appearing.
     * Returns the layer's on-screen bounds in [out] for touch hit-testing.
     */
    fun drawEl(c: Canvas, el: TextEl, w: Float, h: Float, now: Long, pLinear: Float, dirIn: Boolean, out: RectF?) {
        val lines = lines(el, now)
        val size = w * el.size / 100f
        setup(fillP, typeface(el), size, el)
        val lineH = size * (if (el.kind == TextEl.KIND_TIME && el.stacked) 0.92f else 1.15f)
        val widths = lines.map { fillP.measureText(it) }
        val blockW = widths.maxOrNull() ?: 0f
        val blockH = lineH * lines.size
        val cx = el.x * w
        val cy = el.y * h
        out?.set(cx - blockW / 2 - size * 0.2f, cy - blockH / 2 - size * 0.2f, cx + blockW / 2 + size * 0.2f, cy + blockH / 2 + size * 0.2f)
        if (pLinear <= 0.001f) return

        val style = if (dirIn) el.inAnim else el.outAnim
        val p = if (dirIn) easeOut(pLinear) else 1f - easeIn(1f - pLinear)
        val t = now / 1000f
        var alpha = el.opacity / 100f
        var dx = 0f; var dy = 0f; var scale = 1f; var rot = 0f; var flip = 0f
        val sign = if (dirIn) -1f else 1f   // in: comes from behind the motion; out: leaves along it
        val dist = h * 0.07f
        val anim = pLinear < 1f
        if (anim) when (style) {
            A_NONE -> alpha *= if (pLinear > 0f) 1f else 0f
            A_FADE -> alpha *= p
            A_UP -> { dy = sign * -1f * dist * (1 - p); alpha *= p }
            A_DOWN -> { dy = sign * dist * (1 - p); alpha *= p }
            A_LEFT -> { dx = sign * -1f * w * 0.25f * (1 - p); alpha *= p }
            A_RIGHT -> { dx = sign * w * 0.25f * (1 - p); alpha *= p }
            A_ZOOM_IN -> { scale = if (dirIn) 0.4f + 0.6f * p else 1f + 0.6f * (1 - p); alpha *= p }
            A_ZOOM_OUT -> { scale = if (dirIn) 1.6f - 0.6f * p else 0.4f + 0.6f * p; alpha *= p }
            A_POP -> { scale = if (dirIn) easeBack(pLinear) else p; alpha *= min(1f, p * 2f) }
            A_FLIP -> { flip = (1 - p) * 90f * (if (dirIn) 1f else -1f); alpha *= min(1f, p * 1.5f) }
            A_SPIN -> { rot = (1 - p) * 200f * (if (dirIn) -1f else 1f); scale = 0.3f + 0.7f * p; alpha *= p }
            A_GLITCH -> alpha *= if (Math.random() < 0.25 * (1 - p)) 0.2f else min(1f, p * 1.4f)
        }
        // idle motion
        if (el.idle == I_PULSE) scale *= 1f + 0.035f * sin(t * 3f)
        if (el.idle == I_FLOAT) dy += sin(t * 1.4f) * size * 0.08f
        if (el.idle == I_FLICKER) { val f = (t * 6.5f) % 19f; if (f < 0.35f || (f > 4f && f < 4.25f)) alpha *= 0.3f }

        c.save()
        c.translate(cx + dx, cy + dy)
        if (rot != 0f) c.rotate(rot)
        if (scale != 1f) c.scale(scale, scale)
        if (flip != 0f) {
            camera.save(); camera.rotateX(flip); camera.getMatrix(matrix); camera.restore()
            c.concat(matrix)
        }
        val perLetter = anim && (style == A_TYPE || style == A_CASCADE || style == A_WAVE)
        val waveIdle = el.idle == I_WAVE
        lines.forEachIndexed { i, line ->
            val lw = widths[i]
            val lx = when (el.align) { 0 -> -blockW / 2; 2 -> blockW / 2 - lw; else -> -lw / 2 }
            val ly = -blockH / 2 + lineH * i + size * 0.88f
            if (perLetter || waveIdle) {
                val n = line.length.coerceAtLeast(1)
                for (j in line.indices) {
                    val ch = line[j].toString()
                    val px = lx + fillP.measureText(line, 0, j)
                    var la = alpha
                    var ly2 = ly
                    if (perLetter) {
                        val idx = if (dirIn) j else n - 1 - j
                        when (style) {
                            A_TYPE -> la *= if (pLinear * n > idx) 1f else 0f
                            A_CASCADE -> {
                                val lp = easeOut(((pLinear * (n + 4) - idx) / 4f).coerceIn(0f, 1f))
                                la *= lp; ly2 += (1 - lp) * size * 0.6f * (if (dirIn) 1f else -1f)
                            }
                            else -> {
                                val lp = ((pLinear * (n + 3) - idx) / 3f).coerceIn(0f, 1f)
                                la *= lp; ly2 -= sin(lp * PI.toFloat()) * size * 0.35f
                            }
                        }
                    }
                    if (waveIdle) ly2 += sin(t * 4f - j * 0.6f) * size * 0.07f
                    styled(c, ch, px, ly2, el, size, la, now, blockW)
                }
            } else {
                styled(c, line, lx, ly, el, size, alpha, now, blockW)
                if (anim && style == A_GLITCH && pLinear < 1f) {
                    val off = size * 0.08f * (1 - p)
                    val keep = el.color
                    el.color = 0xFFFF2D6F.toInt(); styled(c, line, lx - off * (Math.random() * 2).toFloat(), ly, el, size, alpha * 0.5f * (1 - p), now, blockW)
                    el.color = 0xFF2DE2FF.toInt(); styled(c, line, lx + off * (Math.random() * 2).toFloat(), ly, el, size, alpha * 0.5f * (1 - p), now, blockW)
                    el.color = keep
                }
            }
        }
        c.restore()
    }

    fun needsFrames(d: Design, st: CreatorState, now: Long): Boolean =
        st.busy(now) || d.items.any { it.idle != 0 }

    fun nextDelay(d: Design, st: CreatorState, now: Long): Long = when {
        needsFrames(d, st, now) -> 33L
        d.items.any { it.kind == TextEl.KIND_TIME && it.seconds } -> 1000L - now % 1000L
        else -> 60_000L - now % 60_000L
    }

    /** Full frame: background + every layer. */
    fun draw(c: Canvas, w: Float, h: Float, now: Long, d: Design, st: CreatorState, cache: BgCache, bounds: List<RectF>?) {
        drawBg(c, w, h, d.bg, cache, st.homeT(now))
        d.items.forEachIndexed { i, el ->
            val a = st.anim(i)
            drawEl(c, el, w, h, now, a.progress(now), a.dirIn, bounds?.getOrNull(i))
        }
    }
}
