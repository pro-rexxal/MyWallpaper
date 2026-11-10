package com.rexxal.mywallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Jarvis Mode: hacker robot in his room who photographs whoever wakes the lock screen. */
class JarvisRenderer {
    companion object {
        val ACCENT_NAMES = listOf("Cyan", "Green", "Purple", "Pink", "Amber")
        val ACCENTS = intArrayOf(0xFF3EE8FF.toInt(), 0xFF39FF9E.toInt(), 0xFFB48CFF.toInt(), 0xFFFF5CC8.toInt(), 0xFFFFB84D.toInt())
        // timeline (ms after the trigger)
        const val NOTICE = 0L; const val RAISE = 600L; const val AIM = 1200L; const val FLASH = 1900L; const val TURN1 = 2200L
        const val TYPE = 2900L; const val UPLOAD = 4300L; const val DONE = 5600L; const val TURN2 = 6000L; const val TALK = 6700L; const val END = 10500L
        private val CMDS = listOf("nmap -sV", "ssh root@", "grep -r key", "./exploit", "ping 10.0", "cat /etc/")
        private val PROMPTS = listOf("$ ", "> ", "# ")
    }

    var accent = ACCENTS[0]
    var body = 0xFFE9ECF5.toInt()
    var msgWake = "Gotcha 😎 I saved your picture."
    var msgIntruder = "Intruder saved. The owner will see this 😈"
    var photo: Bitmap? = null

    private var seqKind: String? = null
    private var seqStart = 0L
    private var seqStamp = Date()
    private var shown = false
    private var lastStamp: Date? = null
    private var lastKind = "wake"
    private var lookX = 0f; private var lookY = 0f; private var lookTx = 0f; private var lookTy = 0f; private var nextLook = 0L
    private var blinkAt = 0L; private var nextBlink = 0L

    // fixed random city skyline
    private val cityH = FloatArray(11); private val cityLit = Array(11) { BooleanArray(8) }; private val cityCol = Array(11) { IntArray(8) }
    init {
        val r = java.util.Random(4)
        for (k in 0 until 11) { cityH[k] = .35f + r.nextFloat() * .55f; for (j in 0 until 8) { cityLit[k][j] = r.nextFloat() < .5f; cityCol[k][j] = when { r.nextFloat() < .3f -> 0; r.nextFloat() < .5f -> 1; else -> 2 } } }
    }

    fun trigger(kind: String, now: Long) { seqKind = kind; seqStart = now; seqStamp = Date(now) }
    fun busy(now: Long) = seqKind != null && now - seqStart < END

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpP = Paint(Paint.FILTER_BITMAP_FLAG)
    private val r = RectF()
    private val path = Path()
    private val mono = android.graphics.Typeface.MONOSPACE
    private val bold = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)

    private fun a(col: Int, al: Float) = (col and 0x00FFFFFF) or ((al.coerceIn(0f, 1f) * 255).toInt() shl 24)
    private fun darker(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    private fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, rad: Float, col: Int) { p.shader = null; p.color = col; r.set(x, y, x + w, y + h); c.drawRoundRect(r, rad, rad, p) }
    private fun circle(c: Canvas, x: Float, y: Float, rad: Float, col: Int) { p.shader = null; p.color = col; c.drawCircle(x, y, rad, p) }
    private fun oval(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, col: Int) { p.shader = null; p.color = col; r.set(cx - rx, cy - ry, cx + rx, cy + ry); c.drawOval(r, p) }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, col: Int, tf: android.graphics.Typeface? = null, align: Paint.Align = Paint.Align.CENTER) {
        tp.textSize = size; tp.color = col; tp.typeface = tf; tp.textAlign = align; c.drawText(s, x, y, tp) }
    private fun ease(x: Float) = if (x < .5f) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2

    fun draw(c: Canvas, w: Float, h: Float, now: Long) {
        val t = AnimClock.ms(now)
        val A = accent
        var kind = seqKind
        val st = if (kind != null) now - seqStart else Long.MAX_VALUE / 4
        if (kind != null && st >= DONE) { shown = true; lastStamp = seqStamp; lastKind = kind }
        if (kind != null && st > END) { seqKind = null; kind = null }
        val intr = kind == "intruder"
        fun ph(x: Long, y: Long) = ((st - x).toFloat() / (y - x)).coerceIn(0f, 1f)
        val alert = if (intr && st < END) (sin(t / 140f) + 1) / 2 else 0f
        val BACK = .71f

        // ===== room =====
        p.shader = LinearGradient(0f, 0f, 0f, h * BACK, 0xFF0A0C16.toInt(), 0xFF121628.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h * BACK, p); p.shader = null
        sp.color = a(Color.WHITE, .03f); sp.strokeWidth = 1f; for (k in 1 until 7) c.drawLine(w * k / 7, 0f, w * k / 7, h * BACK, sp)
        p.shader = LinearGradient(0f, 0f, 0f, h * .12f, a(A, .25f), a(A, 0f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h * .12f, p); p.shader = null
        // window + city + blinds
        val WX = w * .05f; val WY = h * .07f; val WW = w * .38f; val WH = h * .17f
        rr(c, WX - w * .012f, WY - w * .012f, WW + w * .024f, WH + w * .024f, w * .012f, 0xFF05060B.toInt())
        c.save(); c.clipRect(WX, WY, WX + WW, WY + WH)
        p.shader = LinearGradient(0f, WY, 0f, WY + WH, 0xFF120A2E.toInt(), 0xFF3B1A4A.toInt(), Shader.TileMode.CLAMP); c.drawRect(WX, WY, WX + WW, WY + WH, p); p.shader = null
        for (k in 0 until 11) {
            val bx = WX + WW * k / 11; val bw = WW / 11 * .85f; val bh = WH * cityH[k]
            p.color = 0xFF0B0A1C.toInt(); c.drawRect(bx, WY + WH - bh, bx + bw, WY + WH, p)
            for (j in 0 until 8) if (cityLit[k][j]) {
                p.color = a(when (cityCol[k][j]) { 0 -> 0xFFFF5CC8.toInt(); 1 -> A; else -> 0xFFFFD68C.toInt() }, .6f + .4f * sin(t / 900f + k + j))
                val lx = bx + bw * .2f + (j % 2) * bw * .4f; val ly = WY + WH - bh + bh * .08f + (j / 2) * bh * .18f; c.drawRect(lx, ly, lx + bw * .18f, ly + bh * .06f, p)
            }
        }
        p.color = a(0xFFFF5CC8.toInt(), .15f); c.drawRect(WX, WY, WX + WW, WY + WH, p)
        c.restore()
        p.color = a(0xFF0A0C16.toInt(), .82f); for (k in 0 until 9) c.drawRect(WX, WY + k * WH / 9, WX + WW, WY + k * WH / 9 + WH / 9 * .55f, p)
        p.color = a(0xFFFF5CC8.toInt(), .08f)
        for (k in 0 until 5) { path.reset(); path.moveTo(WX + WW, WY + k * WH / 5); path.lineTo(WX + WW + w * .35f, WY + h * .12f + k * WH / 5); path.lineTo(WX + WW + w * .35f, WY + h * .12f + k * WH / 5 + WH / 12); path.lineTo(WX + WW, WY + k * WH / 5 + WH / 10); path.close(); c.drawPath(path, p) }
        // shelf + LED
        rr(c, w * .56f, h * .17f, w * .38f, h * .008f, 2f, 0xFF22273D.toInt()); p.color = a(A, .9f); c.drawRect(w * .56f, h * .178f, w * .94f, h * .178f + 2, p)
        p.shader = LinearGradient(0f, h * .178f, 0f, h * .24f, a(A, .25f), a(A, 0f), Shader.TileMode.CLAMP); c.drawRect(w * .56f, h * .178f, w * .94f, h * .24f, p); p.shader = null
        rr(c, w * .58f, h * .135f, w * .05f, h * .035f, 3f, 0xFFFF5A5F.toInt()); rr(c, w * .65f, h * .125f, w * .025f, h * .045f, 3f, 0xFFFFD166.toInt())
        p.color = 0xFF9B6BFF.toInt(); path.reset(); path.moveTo(w * .73f, h * .17f); path.lineTo(w * .75f, h * .13f); path.lineTo(w * .77f, h * .17f); path.close(); c.drawPath(path, p)
        circle(c, w * .83f, h * .155f, w * .022f, 0xFFCFD5E6.toInt()); circle(c, w * .83f, h * .155f, w * .008f, A)
        // poster
        rr(c, w * .72f, h * .04f, w * .2f, h * .1f, 4f, 0xFF0D1120.toInt()); sp.color = a(0xFF39FF9E.toInt(), .7f); sp.strokeWidth = 1.5f; c.drawRect(w * .725f, h * .044f, w * .915f, h * .136f, sp)
        text(c, "ROOT", w * .82f, h * .08f, w * .024f, 0xFF39FF9E.toInt(), mono); text(c, "ACCESS", w * .82f, h * .105f, w * .024f, 0xFF39FF9E.toInt(), mono)
        // router
        rr(c, w * .47f, h * .11f, w * .07f, h * .02f, 3f, 0xFF1D2236.toInt())
        for (k in 0 until 4) { p.color = if (sin(t / (90f + k * 40) + k) > 0) 0xFF39FF9E.toInt() else 0xFF16341F.toInt(); c.drawRect(w * (.478f + k * .014f), h * .117f, w * (.485f + k * .014f), h * .121f, p) }
        sp.color = 0xFF1D2236.toInt(); sp.strokeWidth = w * .004f; c.drawLine(w * .48f, h * .11f, w * .47f, h * .08f, sp); c.drawLine(w * .53f, h * .11f, w * .545f, h * .08f, sp)

        // ===== desk + monitors =====
        val DESK = .6f
        p.color = 0xFF1D2236.toInt(); c.drawRect(w * .495f, h * .25f, w * .505f, h * DESK, p)
        fun mon(x0: Float, y0: Float, x1: Float, y1: Float, skew: Float, content: (Float, Float) -> Unit) {
            c.save(); c.translate((x0 + x1) / 2 * w, (y0 + y1) / 2 * h); c.skew(0f, skew)
            val mw = (x1 - x0) * w; val mh = (y1 - y0) * h
            rr(c, -mw / 2 - w * .012f, -mh / 2 - w * .012f, mw + w * .024f, mh + w * .03f, w * .012f, 0xFF151A2B.toInt())
            rr(c, -mw / 2, -mh / 2, mw, mh, w * .006f, 0xFF03050B.toInt())
            c.save(); c.clipRect(-mw / 2, -mh / 2, mw / 2, mh / 2); content(mw, mh); c.restore()
            p.shader = RadialGradient(0f, mh * .6f, mw, a(if (alert > 0) 0xFFFF3B5C.toInt() else A, .08f + .12f * alert), a(A, 0f), Shader.TileMode.CLAMP); c.drawRect(-mw, -mh / 2, mw, mh * 1.5f, p); p.shader = null
            c.restore()
        }
        fun codeScreen(mw: Float, mh: Float, col: Int, speed: Float, seed: Int) {
            val lh = mh * .085f
            for (k in 0 until 14) {
                val y = -mh / 2 + lh * (k + 1) - ((t / speed) % lh)
                val n = ((k + (t / speed / 9).toInt()) * 2654435761L + seed).toInt() and 0x7FFFFFFF
                text(c, PROMPTS[n % 3] + CMDS[n % 6] + Integer.toHexString(n % 997), -mw / 2 + mw * .05f, y, mw * .05f, a(if (k % 5 == 0) 0xFFFF6EC7.toInt() else col, .8f), mono, Paint.Align.LEFT)
            }
        }
        fun redAlert(mw: Float, mh: Float, label: String) { p.color = a(0xFFFF3B5C.toInt(), .2f + .3f * alert); c.drawRect(-mw / 2, -mh / 2, mw / 2, mh / 2, p); text(c, label, 0f, mh * .05f, mw * .1f, 0xFFFF3B5C.toInt(), bold) }
        mon(.05f, .3f, .29f, .46f, .1f) { mw, mh -> if (alert > 0) redAlert(mw, mh, "⚠ INTRUDER") else codeScreen(mw, mh, A, 70f, 11) }
        mon(.71f, .3f, .95f, .46f, -.1f) { mw, mh ->
            if (alert > 0) redAlert(mw, mh, "WRONG PIN") else {
                sp.color = a(0xFF39FF9E.toInt(), .85f); sp.strokeWidth = mw * .01f
                for (L in 0..1) { path.reset(); for (k in 0..40) { val x = -mw / 2 + mw * k / 40; val y = (if (L == 1) mh * .2f else -mh * .1f) + sin(k * .5f + t / (250f + L * 120)) * mh * .12f * sin(k * .2f + t / 700f); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }; c.drawPath(path, sp) }
                text(c, "NET  " + (40 + (30 * sin(t / 500f)).toInt()) + " Mb/s", -mw * .45f, -mh * .36f, mw * .05f, a(0xFF39FF9E.toInt(), .8f), mono, Paint.Align.LEFT)
            }
        }
        mon(.27f, .24f, .73f, .47f, 0f) { mw, mh ->
            val fs = mw * .045f; val lx = -mw / 2 + mw * .05f
            if (kind != null && st >= TYPE && st < DONE) {
                val file = "capture_" + SimpleDateFormat("HHmmss", Locale.US).format(seqStamp) + ".jpg"
                val lines = listOf("$ cd ~/jarvis/log", "$ sudo upload --secure $file", "> connecting to vault…", "> encrypting…")
                val typed = ((st - TYPE) / 38).toInt(); var used = 0
                lines.forEachIndexed { i, ln ->
                    val n = (typed - used).coerceIn(0, ln.length); used += ln.length + 6
                    val cur = if (n in 1 until ln.length && (t / 250).toInt() % 2 == 1) "▌" else ""
                    text(c, ln.substring(0, n) + cur, lx, -mh / 2 + mh * .14f + i * mh * .11f, fs, if (i < 2) A else 0xFF9AA3C0.toInt(), mono, Paint.Align.LEFT)
                }
                if (st >= UPLOAD) {
                    val pp = ph(UPLOAD, DONE - 150)
                    text(c, "UPLOADING " + (pp * 100).toInt() + "%", lx, mh * .08f, fs, A, mono, Paint.Align.LEFT)
                    sp.color = a(A, .6f); sp.strokeWidth = 2f; c.drawRect(lx, mh * .14f, lx + mw * .9f, mh * .23f, sp)
                    p.color = A; c.drawRect(lx, mh * .14f, lx + mw * .9f * pp, mh * .23f, p)
                }
            } else if (shown) {
                val red = lastKind == "intruder"; val col = if (red) 0xFFFF3B5C.toInt() else A
                val im = photo
                val iw: Float; val ih: Float
                if (im != null) { val s = min(mh * .84f / im.height, mw * .4f / im.width); iw = im.width * s; ih = im.height * s; r.set(-mw * .44f, -ih / 2, -mw * .44f + iw, ih / 2); c.drawBitmap(im, null, r, bmpP) }
                else { iw = mw * .32f; ih = mh * .8f; p.color = 0xFF1A2038.toInt(); c.drawRect(-mw * .44f, -ih / 2, -mw * .44f + iw, ih / 2, p); text(c, "📷", -mw * .44f + iw / 2, mh * .05f, mw * .1f, Color.WHITE) }
                sp.color = col; sp.strokeWidth = 2f; c.drawRect(-mw * .44f, -ih / 2, -mw * .44f + iw, ih / 2, sp)
                p.color = a(col, .35f); val sy = -ih / 2 + ih * ((t / 1400f) % 1f); c.drawRect(-mw * .44f, sy, -mw * .44f + iw, sy + 2, p)
                text(c, if (red) "INTRUDER" else "SUBJECT", mw * .03f, -mh * .24f, mw * .055f, col, mono, Paint.Align.LEFT)
                text(c, "SAVED ✓", mw * .03f, -mh * .07f, mw * .055f, col, mono, Paint.Align.LEFT)
                val d = lastStamp ?: Date()
                text(c, SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(d), mw * .03f, mh * .12f, mw * .042f, 0xFF9AA3C0.toInt(), mono, Paint.Align.LEFT)
                text(c, "~/jarvis/log", mw * .03f, mh * .28f, mw * .042f, 0xFF9AA3C0.toInt(), mono, Paint.Align.LEFT)
            } else {
                text(c, "J.A.R.V.I.S", 0f, -mh * .06f, mw * .075f, A, mono)
                text(c, "monitoring lock screen…", 0f, mh * .14f, mw * .045f, a(A, .55f + .45f * sin(t / 400f)), mono)
            }
        }
        p.shader = LinearGradient(0f, h * DESK, 0f, h * (DESK + .03f), 0xFF262B44.toInt(), 0xFF171B2C.toInt(), Shader.TileMode.CLAMP); c.drawRect(w * .03f, h * DESK, w * .97f, h * (DESK + .03f), p); p.shader = null
        p.color = a(A, .8f); c.drawRect(w * .03f, h * (DESK + .03f) - 2, w * .97f, h * (DESK + .03f), p)
        p.color = 0xFF141827.toInt(); c.drawRect(w * .06f, h * (DESK + .03f), w * .09f, h * BACK, p); c.drawRect(w * .91f, h * (DESK + .03f), w * .94f, h * BACK, p)
        rr(c, w * .33f, h * (DESK - .012f), w * .34f, h * .016f, w * .008f, 0xFF1B2034.toInt())
        for (k in 0 until 14) { p.color = a(if (k % 4 != 0) A else 0xFFFF6EC7.toInt(), .3f + .25f * sin(t / 260f + k)); c.drawRect(w * (.345f + k * .022f), h * (DESK - .008f), w * (.361f + k * .022f), h * DESK, p) }
        rr(c, w * .71f, h * (DESK - .01f), w * .035f, h * .012f, w * .01f, 0xFF1B2034.toInt())
        rr(c, w * .12f, h * (DESK - .035f), w * .06f, h * .035f, w * .01f, 0xFF2A2F48.toInt())
        for (k in 0..1) { val pp = ((t / 1200f) + k / 2f) % 1f; circle(c, w * (.15f + sin(pp * 6) * .01f), h * (DESK - .04f - pp * .04f), w * .01f * (1 + pp), a(Color.WHITE, .22f * (1 - pp))) }
        // PC tower with RGB fans
        val TX = w * .8f; val TY = h * .6f; val TW = w * .16f; val TH = h * .2f
        rr(c, TX, TY, TW, TH, w * .01f, 0xFF11141F.toInt())
        for (k in 0..2) {
            val fx = TX + TW * .5f; val fy = TY + TH * (.2f + k * .3f); val fr = TW * .28f; val col = Color.HSVToColor(floatArrayOf((t / 20f + k * 80) % 360f, .85f, 1f))
            sp.color = col; sp.strokeWidth = w * .006f; c.drawCircle(fx, fy, fr, sp)
            c.save(); c.translate(fx, fy); c.rotate(t / 90f * 57.3f % 360f); p.color = a(col, .35f)
            for (b in 0 until 5) { c.rotate(72f); r.set(0f, -fr * .16f, fr * .9f, fr * .16f); c.drawOval(r, p) }
            c.restore()
        }
        sp.color = 0xFF0C0E17.toInt(); sp.strokeWidth = w * .008f
        path.reset(); path.moveTo(TX, TY + TH * .2f); path.quadTo(w * .72f, h * .7f, w * .6f, h * (DESK + .03f)); c.drawPath(path, sp)
        // floor + rug + glow
        p.shader = LinearGradient(0f, h * BACK, 0f, h, 0xFF0F1220.toInt(), 0xFF06070D.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, h * BACK, w, h, p); p.shader = null
        oval(c, w * .5f, h * .86f, w * .46f, h * .07f, 0xFF161A2E.toInt()); sp.color = a(A, .25f); sp.strokeWidth = 2f; r.set(w * .08f, h * .802f, w * .92f, h * .918f); c.drawOval(r, sp)

        // ===== Jarvis in his chair =====
        var turn = 0f
        if (kind != null) turn = when { st in TURN1 until TYPE -> ease(ph(TURN1, TYPE)); st in TYPE until TURN2 -> 1f; st in TURN2 until TALK -> 1 - ease(ph(TURN2, TALK)); else -> 0f }
        val facingBack = turn > .5f; val sx = max(.05f, abs(cos(turn * PI.toFloat())))
        val cx = w * .5f; val S = w * .3f; val cy = h * .73f + sin(t / 700f) * S * .015f; val B = body; val shade = darker(B, .82f)
        if (now > nextLook) { lookTx = (Math.random() * 2 - 1).toFloat(); lookTy = (Math.random() * 1.2 - .2).toFloat(); nextLook = now + 1200 + (Math.random() * 2200).toLong() }
        var lx = lookTx; var ly = lookTy; var eyeScale = 1f; var mood = "idle"
        if (kind == null && sin(t / 3000f) > .3f) { lx = .55f; ly = .9f; mood = "phone" }
        if (kind != null) { if (st < FLASH + 200) { lx = 0f; ly = 0f; eyeScale = if (st < RAISE) 1 + .35f * sin(ph(0, RAISE) * PI.toFloat()) else 1.08f; mood = "focus" } else if (st >= TALK) { lx = 0f; ly = .1f; mood = if (intr) "evil" else "happy" } }
        lookX += (lx - lookX) * .15f; lookY += (ly - lookY) * .15f
        // chair base
        p.color = 0xFF0C0E17.toInt(); c.drawRect(cx - w * .008f, cy + S * .35f, cx + w * .008f, cy + S * .7f, p)
        for (k in floatArrayOf(-1f, -.4f, .4f, 1f)) c.drawCircle(cx + k * S * .45f, cy + S * .72f, S * .045f, p)
        sp.color = 0xFF0C0E17.toInt(); sp.strokeWidth = w * .01f; c.drawLine(cx - S * .45f, cy + S * .7f, cx + S * .45f, cy + S * .7f, sp)
        fun chairBack() { c.save(); c.translate(cx, 0f); c.scale(sx, 1f); rr(c, -S * .62f, cy - S * .85f, S * 1.24f, S * 1.15f, S * .25f, 0xFF1A1E33.toInt()); rr(c, -S * .5f, cy - S * .75f, S * 1f, S * .95f, S * .2f, 0xFF232845.toInt())
            p.color = a(A, .8f); c.drawRect(-S * .5f, cy - S * .3f, -S * .46f, cy + S * .15f, p); c.drawRect(S * .46f, cy - S * .3f, S * .5f, cy + S * .15f, p); c.restore() }
        fun seat() = rr(c, cx - S * .6f * sx, cy + S * .22f, S * 1.2f * sx, S * .16f, S * .08f, 0xFF1A1E33.toInt())
        fun arm(shx: Float, shy: Float, hx: Float, hy: Float, bend: Float) {
            val mx = (shx + hx) / 2 + bend; val my = (shy + hy) / 2 + abs(bend) * .4f
            path.reset(); path.moveTo(shx, shy); path.quadTo(mx, my, hx, hy)
            sp.color = shade; sp.strokeWidth = S * .11f; c.drawPath(path, sp); sp.color = B; sp.strokeWidth = S * .07f; c.drawPath(path, sp)
            circle(c, hx, hy, S * .1f, shade); circle(c, hx - S * .015f, hy - S * .015f, S * .085f, B)
        }
        fun phone(px: Float, py: Float, pw: Float, back: Boolean, scroll: Boolean, lit: Boolean = false) {
            val phh = pw * 1.85f; c.save(); c.translate(px, py)
            rr(c, -pw / 2, -phh / 2, pw, phh, pw * .16f, 0xFF1B1F30.toInt())
            if (back) { rr(c, -pw * .38f, -phh * .42f, pw * .3f, pw * .3f, pw * .06f, 0xFF0B0E18.toInt()); circle(c, -pw * .23f, -phh * .42f + pw * .15f, pw * .08f, 0xFF3A4466.toInt()); circle(c, -pw * .23f, -phh * .42f + pw * .15f, pw * .035f, A) }
            else { rr(c, -pw * .42f, -phh * .45f, pw * .84f, phh * .9f, pw * .1f, 0xFF05070E.toInt())
                if (scroll) { p.color = a(A, .25f); for (k in 0 until 5) { val yy = -phh * .36f + k * phh * .15f + ((t / 40f) % (phh * .15f)); c.drawRect(-pw * .32f, yy, pw * .32f, yy + phh * .07f, p) } }
                if (lit) { p.color = a(A, .5f); c.drawRect(-pw * .3f, -phh * .1f, pw * .3f, phh * .1f, p) } }
            c.restore()
        }
        val hy = cy - S * .62f; val bodyY = cy
        val shL = floatArrayOf(cx - S * .4f * sx, bodyY - S * .12f); val shR = floatArrayOf(cx + S * .4f * sx, bodyY - S * .12f)
        if (!facingBack) {
            chairBack()
            c.save(); c.translate(cx, 0f); c.scale(sx, 1f)
            oval(c, 0f, bodyY + S * .06f, S * .48f, S * .4f, shade); oval(c, 0f, bodyY + S * .02f, S * .44f, S * .37f, B); oval(c, 0f, bodyY + S * .06f, S * .22f, S * .16f, 0xFF0B0E1A.toInt())
            sp.color = A; sp.strokeWidth = S * .02f; path.reset()
            for (k in 0..18) { val x = -S * .17f + S * .34f * k / 18; val y = bodyY + S * .06f + sin(k * .9f + t / 110f) * S * .05f * (if (kind != null) 1f else .3f); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            c.drawPath(path, sp)
            sp.color = shade; sp.strokeWidth = S * .1f
            for (sd in floatArrayOf(-1f, 1f)) { c.drawLine(sd * S * .18f, bodyY + S * .3f, sd * S * .2f, bodyY + S * .52f + sin(t / 500f + sd) * S * .02f, sp); oval(c, sd * S * .22f, bodyY + S * .56f, S * .1f, S * .05f, shade) }
            c.restore()
            seat()
            val raise = if (kind == null) 0f else when { st < RAISE -> 0f; st < AIM -> ease(ph(RAISE, AIM)); st < TURN1 -> 1f; else -> 1 - ease(ph(TURN1, TURN1 + 400)) }
            val idleX = cx + S * .28f * sx; val idleY = bodyY + S * .02f + (if (mood == "phone") sin(t / 600f) * S * .02f else 0f)
            val aimX = cx + S * .18f * sx; val aimY = hy + S * .12f
            val hRx = idleX + (aimX - idleX) * raise; val hRy = idleY + (aimY - idleY) * raise
            arm(shL[0], shL[1], cx - S * .58f * sx, bodyY + S * .24f, -S * .12f)
            // head
            c.save(); c.translate(cx, 0f); c.scale(sx, 1f)
            rr(c, -S * .62f, hy - S * .42f, S * 1.24f, S * .84f, S * .38f, shade); rr(c, -S * .6f, hy - S * .44f, S * 1.2f, S * .82f, S * .36f, B)
            p.color = a(Color.WHITE, .45f); c.save(); c.rotate(-11f, -S * .32f, hy - S * .32f); r.set(-S * .48f, hy - S * .37f, -S * .16f, hy - S * .27f); c.drawOval(r, p); c.restore()
            rr(c, -S * .74f, hy - S * .14f, S * .15f, S * .32f, S * .06f, 0xFF1D2236.toInt()); rr(c, S * .59f, hy - S * .14f, S * .15f, S * .32f, S * .06f, 0xFF1D2236.toInt())
            p.color = A; c.drawRect(-S * .71f, hy - S * .03f, -S * .62f, hy + S * .04f, p); c.drawRect(S * .62f, hy - S * .03f, S * .71f, hy + S * .04f, p)
            sp.color = 0xFF1D2236.toInt(); sp.strokeWidth = S * .055f; r.set(-S * .67f, hy - S * .73f, S * .67f, hy + S * .61f); c.drawArc(r, 201.6f, 136.8f, false, sp)
            sp.strokeWidth = S * .025f; path.reset(); path.moveTo(-S * .66f, hy + S * .1f); path.quadTo(-S * .6f, hy + S * .32f, -S * .25f, hy + S * .3f); c.drawPath(path, sp); circle(c, -S * .24f, hy + S * .3f, S * .03f, A)
            rr(c, -S * .5f, hy - S * .32f, S * 1f, S * .6f, S * .26f, 0xFF0B0E1A.toInt())
            if (now > nextBlink) { blinkAt = now; nextBlink = now + 2500 + (Math.random() * 4000).toLong() }
            val bl = (now - blinkAt) / 150f; val blink = if (bl in 0f..1f) max(.08f, abs(1 - bl * 2)) else 1f
            for (sd in floatArrayOf(-1f, 1f)) {
                val ex = sd * S * .22f; val ey = hy - S * .03f; val er = S * .17f * eyeScale
                if (mood == "happy") { sp.color = A; sp.strokeWidth = S * .05f; r.set(ex - er * .6f, ey + er * .25f - er * .6f, ex + er * .6f, ey + er * .25f + er * .6f); c.drawArc(r, 207f, 126f, false, sp); continue }
                oval(c, ex, ey, er, er * blink, 0xFFF4F7FF.toInt())
                if (blink > .3f) { val px = ex + lookX * er * .38f; val py = ey + lookY * er * .38f
                    circle(c, px, py, er * .55f, if (mood == "evil") 0xFFFF3B5C.toInt() else A); circle(c, px, py, er * .3f, 0xFF05060C.toInt())
                    circle(c, px - er * .18f, py - er * .2f, er * .13f, Color.WHITE); circle(c, px + er * .15f, py + er * .12f, er * .06f, Color.WHITE) }
                if (mood == "evil") { sp.color = 0xFF0B0E1A.toInt(); sp.strokeWidth = S * .05f; c.drawLine(ex - er * 1.05f, ey - er * (if (sd < 0) .9f else .5f), ex + er * 1.05f, ey - er * (if (sd < 0) .5f else .9f), sp) }
            }
            sp.color = A; sp.strokeWidth = S * .035f
            when (mood) {
                "happy", "evil" -> { r.set(-S * .12f, hy + S * .01f, S * .12f, hy + S * .25f); c.drawArc(r, 27f, 126f, false, sp) }
                "focus" -> c.drawLine(-S * .06f, hy + S * .2f, S * .06f, hy + S * .2f, sp)
                else -> { r.set(-S * .07f, hy + S * .05f, S * .07f, hy + S * .19f); c.drawArc(r, 36f, 108f, false, sp) }
            }
            c.restore()
            arm(shR[0], shR[1], hRx, hRy, S * .14f * (1 - raise))
            val pw = S * (.2f + .08f * raise)
            if (raise > .5f) phone(hRx, hRy - pw * .6f, pw, back = true, scroll = false) else phone(hRx - S * .02f, hRy - pw * .5f, pw, back = false, scroll = true)
            if (kind != null && st in AIM until FLASH) { val n = 3 - min(2L, (st - AIM) / ((FLASH - AIM) / 3)); text(c, n.toString(), hRx, hRy - pw * 1.6f, S * .28f, if (intr) 0xFFFF3B5C.toInt() else A, bold) }
            if (kind != null && st < RAISE + 200) text(c, "!", cx + S * .78f, hy - S * .45f - sin(ph(0, RAISE) * PI.toFloat()) * S * .1f, S * .3f, if (intr) 0xFFFF3B5C.toInt() else 0xFFFFD166.toInt(), bold)
        } else {
            val typing = kind != null && st >= TYPE && st < DONE + 200
            c.save(); c.translate(cx, 0f); c.scale(sx, 1f)
            rr(c, -S * .62f, hy - S * .42f, S * 1.24f, S * .84f, S * .38f, shade); rr(c, -S * .6f, hy - S * .44f, S * 1.2f, S * .82f, S * .36f, B)
            rr(c, -S * .74f, hy - S * .14f, S * .15f, S * .32f, S * .06f, 0xFF1D2236.toInt()); rr(c, S * .59f, hy - S * .14f, S * .15f, S * .32f, S * .06f, 0xFF1D2236.toInt())
            sp.color = 0xFF1D2236.toInt(); sp.strokeWidth = S * .055f; r.set(-S * .67f, hy - S * .73f, S * .67f, hy + S * .61f); c.drawArc(r, 201.6f, 136.8f, false, sp)
            c.restore()
            val kY = h * (DESK - .004f)
            fun tap(k: Float) = if (typing) max(0f, sin(t / 70f + k)) * S * .05f else 0f
            arm(shL[0], shL[1] - S * .05f, cx - S * .48f, kY - tap(0f), -S * .12f); arm(shR[0], shR[1] - S * .05f, cx + S * .48f, kY - tap(1.7f), S * .12f)
            chairBack(); seat()
            phone(w * .27f, h * (DESK - .012f), S * .14f, back = false, scroll = false, lit = true)
            sp.color = a(A, .7f); sp.strokeWidth = w * .004f; path.reset(); path.moveTo(w * .27f, h * (DESK - .012f) + S * .13f); path.quadTo(w * .35f, h * (DESK + .03f), w * .5f, h * (DESK + .02f)); c.drawPath(path, sp)
            if (typing && Math.random() < .3) text(c, listOf("0", "1", "{", "}", ";").random(), cx + ((Math.random() - .5) * S * .6f).toFloat(), kY - S * .25f - (Math.random() * S * .2f).toFloat(), S * .12f, a(A, .9f), mono)
        }
        // effects
        if (alert > 0) { p.color = a(0xFFFF2846.toInt(), .12f * alert); c.drawRect(0f, 0f, w, h, p) }
        if (kind != null && st in FLASH until FLASH + 350) { val f = 1 - ph(FLASH, FLASH + 350); p.color = a(Color.WHITE, .92f * f); c.drawRect(0f, 0f, w, h, p); text(c, "CLICK!", w * .5f, h * .9f, w * .08f, a(0xFF0B0E1A.toInt(), f), bold) }
        val say = if (kind == null) null else when { st < AIM -> if (intr) "Wrong PIN! Smile 📸" else "Hey! Hold still 📸"; st in TALK until END -> if (intr) msgIntruder else msgWake; else -> null }
        if (say != null) {
            tp.textSize = w * .04f; tp.typeface = bold; val tw = tp.measureText(say); val pad = w * .03f; val bw = min(w * .92f, tw + pad * 2); val bh = w * .04f + pad * 1.4f
            val bx = (cx - bw / 2).coerceIn(w * .04f, w * .96f - bw); val by = hy - S * .6f - bh
            rr(c, bx, by, bw, bh, bh / 2, a(Color.WHITE, .96f))
            path.reset(); path.moveTo(cx - w * .02f, by + bh - 1); path.lineTo(cx, by + bh + w * .025f); path.lineTo(cx + w * .02f, by + bh - 1); path.close(); p.color = a(Color.WHITE, .96f); c.drawPath(path, p)
            text(c, say, bx + bw / 2, by + bh / 2 + w * .014f, w * .04f, 0xFF0B0E1A.toInt(), bold)
        }
    }
}
