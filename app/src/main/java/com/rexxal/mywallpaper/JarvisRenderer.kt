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
        // the photo is taken the instant it triggers; the quick grab + flash is only the animation
        const val NOTICE = 0L; const val RAISE = 0L; const val AIM = 170L; const val FLASH = 200L; const val TURN1 = 1500L
        const val TYPE = 2200L; const val UPLOAD = 3500L; const val DONE = 4800L; const val TURN2 = 5200L; const val TALK = 5900L; const val END = 9700L
        private val POKES = listOf("Yes? What do you need? 🤖", "I'm busy hacking, make it quick 😤", "Hey! That tickles 😆", "Need something, boss?", "Don't poke the genius 🧠", "I'm watching the lock screen for you 👀", "Coffee break? Not now ☕")
        // x0, y0, x1, y1, line
        private val SPOTS = listOf(floatArrayOf(.27f, .24f, .73f, .47f) to "Hands off my main screen! 🖥", floatArrayOf(.05f, .3f, .29f, .46f) to "That's classified code 🤫",
            floatArrayOf(.71f, .3f, .95f, .46f) to "Network looks clean 📡", floatArrayOf(.8f, .63f, .96f, .83f) to "Careful, that's my baby 💻", floatArrayOf(.02f, .03f, .48f, .28f) to "Nice view, right? 🌆",
            floatArrayOf(.12f, .55f, .18f, .6f) to "My coffee! ☕", floatArrayOf(.72f, .04f, .92f, .14f) to "Root access. Always 😎")
        private val CMDS = listOf("nmap -sV", "ssh root@", "grep -r key", "./exploit", "ping 10.0", "cat /etc/")
        private val PROMPTS = listOf("$ ", "> ", "# ")
    }

    var accent = ACCENTS[0]
    var body = 0xFFE9ECF5.toInt()
    var msgWake = "Gotcha 😎 I saved your picture."
    var msgIntruder = "Intruder saved. The owner will see this 😈"
    var msgOwner = "Hi sir 👋 Welcome back."
    var msgStranger = "Who are you?! 🤨 I've saved your face."
    var msgNoFace = "I can't see you… turn on a light? 🔦"
    private var score = 0
    private var lastNow = 0L
    var photo: Bitmap? = null

    private var seqKind: String? = null
    private var seqStart = 0L
    private var seqStamp = Date()
    private var shown = false
    private var lastStamp: Date? = null
    private var lastKind = "wake"
    private var lookX = 0f; private var lookY = 0f; private var lookTx = 0f; private var lookTy = 0f; private var nextLook = 0L
    private var blinkAt = 0L; private var nextBlink = 0L
    // room theme + real outdoor world
    var light = false
    var weather = "Clear"
    var lat = 22f; var lon = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000f * 15f
    // touches + attention
    private var tapX = 0f; private var tapY = 0f; private var tapAt = 0L
    private val ripples = ArrayList<FloatArray>()   // x, y, time(as float offset)
    private val rippleT = ArrayList<Long>()
    private val taps = ArrayList<Long>()
    private var pokeLine: String? = null; private var pokeMood = "idle"; private var pokeAt = 0L; private var pokeN = 0; private var bumpAt = 0L
    private var attentionUntil = 0L; private var attentionAt = 0L
    // outdoor events
    private var evKind: String? = null; private var evStart = 0L; private var evNext = 0L; private var evY = .2f; private var evDir = 1

    // sound hooks (set by the wallpaper)
    var onSfx: ((String) -> Unit)? = null
    var onSay: ((String) -> Unit)? = null
    private var lastSay: String? = null
    private var sndSeqStart = -1L; private var sndShut = false; private var sndEnd = false; private var sndKey = -1
    // motion sensors (work on the lock screen)
    var tiltX = 0f; var tiltY = 0f
    private var shakeAt = -100_000L; private var pickAt = -100_000L; private var coverUntil = 0L
    private var messStart = -1L; private val messAng = FloatArray(5); private var messLine = ""; private val messDone = BooleanArray(8)
    private val FIX_P = arrayOf(floatArrayOf(.82f, .09f), floatArrayOf(.17f, .38f), floatArrayOf(.5f, .355f), floatArrayOf(.83f, .38f), floatArrayOf(.15f, .585f))
    private val FIX_LINE = arrayOf("My poster…", "Left screen…", "Easy now…", "Almost there…", "My coffee 😢")
    private val M_SHAKE = 1200L; private val M_STAND = 1800L; private val M_STEP = 800L; private val M_SIT = 5800L; private val M_END = 7000L

    fun motion(kind: String, now: Long) {
        if (seqKind != null && now - seqStart < END) return
        val rnd = { (if (Math.random() < .5) -1f else 1f) }
        when (kind) {
            "shake" -> { if (messStart >= 0) return; shakeAt = now; pokeLine = null; messStart = now; messDone.fill(false)
                for (i in 0..3) messAng[i] = rnd() * (.12f + (Math.random() * .18).toFloat()) * (if (i == 0) 1.6f else if (i == 2) .7f else 1f); messAng[4] = rnd() * 1.45f
                messLine = listOf("Whoa! Stop shaking me 😵", "Earthquake?! 😱", "Hey hey HEY! 😵").random(); onSfx?.invoke("grumble") }
            "pick" -> { if (messStart >= 0) return; pickAt = now; poke(listOf("Oh, you're back 👀", "Hey there! 👋", "Picked me up? Hi! 🤖").random(), "happy", now); onSfx?.invoke("chirp") }
            "cover" -> { if (messStart >= 0) return; coverUntil = now + 4000; poke("Hey! I can't see! 🙈", "squint", now); onSfx?.invoke("hmm") }
            "uncover" -> { if (now < coverUntil) { coverUntil = 0; poke("Peekaboo! 😄", "happy", now); onSfx?.invoke("beep") } }
        }
    }
    private fun mAng(i: Int, now: Long): Float {
        if (messStart < 0 || i < 0) return 0f
        val m = now - messStart; val a = messAng[i]
        if (m < M_SHAKE) return a * min(1f, m / 500f) + sin(m / 40f) * .03f
        val f = M_STAND + i * M_STEP
        if (m < f + 480) return a
        if (m < f + M_STEP - 60) { val q = (m - f - 480f) / (M_STEP - 540f); return a * (1 - ease(q)) + sin(m / 30f) * .02f * (1 - q) }
        return 0f
    }

    /** Someone woke the locked phone: lean in and watch the keypad. */
    fun attention(now: Long, on: Boolean, keep: Boolean = false) {
        if (!on) { attentionUntil = 0L; return }
        if (!keep || now > attentionUntil) attentionAt = now     // a new wake-up restarts the "someone's unlocking" line
        attentionUntil = now + if (keep) 1500 else 9000
    }

    /** A tap on the home screen (normalised 0..1). */
    fun tap(nx: Float, ny: Float, now: Long, w: Float, h: Float) {
        tapX = nx; tapY = ny; tapAt = now; ripples.add(floatArrayOf(nx, ny)); rippleT.add(now)
        if (seqKind != null && now - seqStart < END) return
        taps.removeAll { now - it > 1500 }; taps.add(now)
        if (messStart >= 0) return
        if (taps.size >= 4) { poke("Okay okay, I see you! Stop tapping 😤", "evil", now); taps.clear(); onSfx?.invoke("grumble"); return }
        val S = .3f * w / h
        if (abs(nx - .5f) < .2f && ny > .73f - 1.07f * S && ny < .73f + .45f * S) { poke(POKES[pokeN++ % POKES.size], "happy", now); bumpAt = now; onSfx?.invoke("beep"); return }
        SPOTS.firstOrNull { (r, _) -> nx >= r[0] && nx <= r[2] && ny >= r[1] && ny <= r[3] }?.let { poke(it.second, "idle", now) }
    }
    private fun poke(line: String, mood: String, now: Long) { pokeLine = line; pokeMood = mood; pokeAt = now }

    // fixed random city skyline
    private val cityH = FloatArray(11); private val cityLit = Array(11) { BooleanArray(8) }; private val cityCol = Array(11) { IntArray(8) }
    init {
        val r = java.util.Random(4)
        for (k in 0 until 11) { cityH[k] = .35f + r.nextFloat() * .55f; for (j in 0 until 8) { cityLit[k][j] = r.nextFloat() < .5f; cityCol[k][j] = when { r.nextFloat() < .3f -> 0; r.nextFloat() < .5f -> 1; else -> 2 } } }
    }

    /** Face check finished: "owner", "stranger" or "noface". */
    fun resolve(kind: String, sc: Int) { if (seqKind == "check") { seqKind = kind; score = sc } }

    fun trigger(kind: String, now: Long) { seqKind = kind; seqStart = now; seqStamp = Date(now); shown = false; photo = null }
    /** Clears the monitor back to "monitoring…" (old photo removed). */
    fun clear() { if (seqKind == null) { shown = false; photo = null } }
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
    private fun pal(dark: Long, lite: Long) = (if (light) lite else dark).toInt()
    private fun mix(a: Int, b: Int, k: Float): Int { val q = k.coerceIn(0f, 1f); return Color.rgb((Color.red(a) + (Color.red(b) - Color.red(a)) * q).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * q).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * q).toInt()) }
    private fun ease(x: Float) = if (x < .5f) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2

    fun draw(c: Canvas, w: Float, h: Float, now: Long) {
        val t = AnimClock.ms(now)
        val A = accent
        // while the face check is still running, hold the "matching" part of the animation
        if (seqKind == "check" && now - seqStart > DONE - 400) seqStart += (now - lastNow).coerceIn(0L, 200L)
        lastNow = now
        var kind = seqKind
        val st = if (kind != null) now - seqStart else Long.MAX_VALUE / 4
        if (kind != null && st >= DONE) { shown = true; lastStamp = seqStamp; lastKind = kind }
        if (kind != null && st > END) { seqKind = null; kind = null }
        val face = kind == "check" || kind == "owner" || kind == "stranger" || kind == "noface"
        val intr = kind == "intruder" || (kind == "stranger" && st >= DONE)
        val GOOD = 0xFF39FF9E.toInt()
        fun ph(x: Long, y: Long) = ((st - x).toFloat() / (y - x)).coerceIn(0f, 1f)
        val alert = if (intr && st < END) (sin(t / 140f) + 1) / 2 else 0f
        val BACK = .71f
        if (messStart >= 0 && now - messStart > M_END) messStart = -1L
        val mt = if (messStart >= 0) now - messStart else Long.MAX_VALUE / 4; val messing = messStart >= 0
        c.save()
        if (messing && mt < M_SHAKE) { val k = 1 - mt / M_SHAKE.toFloat(); c.translate(w / 2, h / 2); c.rotate(sin(t / 37f) * .025f * k * 57.3f); c.translate(-w / 2 + sin(t / 23f) * w * .015f * k, -h / 2 + cos(t / 29f) * h * .008f * k) }

        // ===== room =====
        p.shader = LinearGradient(0f, 0f, 0f, h * BACK, pal(0xFF0A0C16, 0xFFF1F2F6), pal(0xFF121628, 0xFFDFE3EC), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h * BACK, p); p.shader = null
        sp.color = if (light) a(Color.BLACK, .05f) else a(Color.WHITE, .03f); sp.strokeWidth = 1f; for (k in 1 until 7) c.drawLine(w * k / 7, 0f, w * k / 7, h * BACK, sp)
        p.shader = LinearGradient(0f, 0f, 0f, h * .12f, a(A, .25f), a(A, 0f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h * .12f, p); p.shader = null
        drawWindow(c, w, h, t, now)
        // shelf + LED
        rr(c, w * .56f, h * .17f, w * .38f, h * .008f, 2f, pal(0xFF22273D, 0xFFC3C9D6)); p.color = a(A, .9f); c.drawRect(w * .56f, h * .178f, w * .94f, h * .178f + 2, p)
        p.shader = LinearGradient(0f, h * .178f, 0f, h * .24f, a(A, .25f), a(A, 0f), Shader.TileMode.CLAMP); c.drawRect(w * .56f, h * .178f, w * .94f, h * .24f, p); p.shader = null
        rr(c, w * .58f, h * .135f, w * .05f, h * .035f, 3f, 0xFFFF5A5F.toInt()); rr(c, w * .65f, h * .125f, w * .025f, h * .045f, 3f, 0xFFFFD166.toInt())
        p.color = 0xFF9B6BFF.toInt(); path.reset(); path.moveTo(w * .73f, h * .17f); path.lineTo(w * .75f, h * .13f); path.lineTo(w * .77f, h * .17f); path.close(); c.drawPath(path, p)
        circle(c, w * .83f, h * .155f, w * .022f, 0xFFCFD5E6.toInt()); circle(c, w * .83f, h * .155f, w * .008f, A)
        // poster
        c.save(); c.rotate(mAng(0, now) * 57.3f, w * .82f, h * .04f)
        rr(c, w * .72f, h * .04f, w * .2f, h * .1f, 4f, 0xFF0D1120.toInt()); sp.color = a(0xFF39FF9E.toInt(), .7f); sp.strokeWidth = 1.5f; c.drawRect(w * .725f, h * .044f, w * .915f, h * .136f, sp)
        text(c, "ROOT", w * .82f, h * .08f, w * .024f, 0xFF39FF9E.toInt(), mono); text(c, "ACCESS", w * .82f, h * .105f, w * .024f, 0xFF39FF9E.toInt(), mono); c.restore()
        // router
        c.save(); c.translate(w * .12f, -h * .035f)
        rr(c, w * .47f, h * .11f, w * .07f, h * .02f, 3f, pal(0xFF1D2236, 0xFFB9C0CF))
        for (k in 0 until 4) { p.color = if (sin(t / (90f + k * 40) + k) > 0) 0xFF39FF9E.toInt() else 0xFF16341F.toInt(); c.drawRect(w * (.478f + k * .014f), h * .117f, w * (.485f + k * .014f), h * .121f, p) }
        sp.color = pal(0xFF1D2236, 0xFFB9C0CF); sp.strokeWidth = w * .004f; c.drawLine(w * .48f, h * .11f, w * .47f, h * .08f, sp); c.drawLine(w * .53f, h * .11f, w * .545f, h * .08f, sp)
        c.restore()

        // ===== desk + monitors =====
        val DESK = .6f
        p.color = pal(0xFF1D2236, 0xFFB9C0CF); c.drawRect(w * .495f, h * .25f, w * .505f, h * DESK, p)
        fun mon(x0: Float, y0: Float, x1: Float, y1: Float, skew: Float, id: Int, content: (Float, Float) -> Unit) {
            c.save(); c.translate((x0 + x1) / 2 * w, (y0 + y1) / 2 * h); c.rotate(mAng(id, now) * 57.3f); c.skew(0f, skew)
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
        mon(.05f, .3f, .29f, .46f, .1f, 1) { mw, mh -> if (alert > 0) redAlert(mw, mh, if (kind == "stranger") "⚠ UNKNOWN" else "⚠ INTRUDER") else codeScreen(mw, mh, A, 70f, 11) }
        mon(.71f, .3f, .95f, .46f, -.1f, 3) { mw, mh ->
            if (alert > 0) redAlert(mw, mh, if (kind == "stranger") "NOT OWNER" else "WRONG PIN") else {
                sp.color = a(0xFF39FF9E.toInt(), .85f); sp.strokeWidth = mw * .01f
                for (L in 0..1) { path.reset(); for (k in 0..40) { val x = -mw / 2 + mw * k / 40; val y = (if (L == 1) mh * .2f else -mh * .1f) + sin(k * .5f + t / (250f + L * 120)) * mh * .12f * sin(k * .2f + t / 700f); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }; c.drawPath(path, sp) }
                text(c, "NET  " + (40 + (30 * sin(t / 500f)).toInt()) + " Mb/s", -mw * .45f, -mh * .36f, mw * .05f, a(0xFF39FF9E.toInt(), .8f), mono, Paint.Align.LEFT)
            }
        }
        mon(.27f, .24f, .73f, .47f, 0f, 2) { mw, mh ->
            val fs = mw * .045f; val lx = -mw / 2 + mw * .05f
            if (kind != null && st >= TYPE && st < DONE) {
                val file = "capture_" + SimpleDateFormat("HHmmss", Locale.US).format(seqStamp) + ".jpg"
                val lines = if (face) listOf("$ cd ~/jarvis/face", "$ face_match --owner $file", "> detecting face…", if (kind == "noface") "> no face in frame" else "> comparing with owner…")
                    else listOf("$ cd ~/jarvis/log", "$ sudo upload --secure $file", "> connecting to vault…", "> encrypting…")
                val typed = ((st - TYPE) / 38).toInt(); var used = 0
                lines.forEachIndexed { i, ln ->
                    val n = (typed - used).coerceIn(0, ln.length); used += ln.length + 6
                    val cur = if (n in 1 until ln.length && (t / 250).toInt() % 2 == 1) "▌" else ""
                    text(c, ln.substring(0, n) + cur, lx, -mh / 2 + mh * .14f + i * mh * .11f, fs, if (i < 2) A else 0xFF9AA3C0.toInt(), mono, Paint.Align.LEFT)
                }
                if (st >= UPLOAD) {
                    val pp = ph(UPLOAD, DONE - 150)
                    text(c, (if (face) "MATCHING " else "UPLOADING ") + (pp * 100).toInt() + "%", lx, mh * .08f, fs, A, mono, Paint.Align.LEFT)
                    sp.color = a(A, .6f); sp.strokeWidth = 2f; c.drawRect(lx, mh * .14f, lx + mw * .9f, mh * .23f, sp)
                    p.color = A; c.drawRect(lx, mh * .14f, lx + mw * .9f * pp, mh * .23f, p)
                }
            } else if (shown && face && kind != null && kind != "check") {
                val res = when (kind) { "owner" -> Triple("MATCH ✓", "$score% · OWNER", GOOD); "stranger" -> Triple("NO MATCH ✗", "$score% · UNKNOWN", 0xFFFF3B5C.toInt()); else -> Triple("NO FACE", "FOUND", 0xFFFFB84D.toInt()) }
                val im = photo
                val iw: Float; val ih: Float
                if (im != null && kind != "noface") { val s2 = min(mh * .84f / im.height, mw * .4f / im.width); iw = im.width * s2; ih = im.height * s2; r.set(-mw * .44f, -ih / 2, -mw * .44f + iw, ih / 2); c.drawBitmap(im, null, r, bmpP) }
                else { iw = mw * .32f; ih = mh * .8f; p.color = 0xFF05060C.toInt(); c.drawRect(-mw * .44f, -ih / 2, -mw * .44f + iw, ih / 2, p); text(c, "?", -mw * .44f + iw / 2, mh * .05f, mw * .12f, 0xFF2A2F45.toInt(), bold) }
                sp.color = res.third; sp.strokeWidth = 2f; c.drawRect(-mw * .44f, -ih / 2, -mw * .44f + iw, ih / 2, sp)
                text(c, res.first, mw * .03f, -mh * .18f, mw * .065f, res.third, mono, Paint.Align.LEFT)
                text(c, res.second, mw * .03f, 0f, mw * .048f, res.third, mono, Paint.Align.LEFT)
                text(c, when (kind) { "owner" -> "photo deleted"; "stranger" -> "saved to log"; else -> "too dark?" }, mw * .03f, mh * .2f, mw * .04f, 0xFF9AA3C0.toInt(), mono, Paint.Align.LEFT)
            } else if (shown && !face) {
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
        p.shader = LinearGradient(0f, h * DESK, 0f, h * (DESK + .03f), pal(0xFF262B44, 0xFFD9C2A0), pal(0xFF171B2C, 0xFFBBA17D), Shader.TileMode.CLAMP); c.drawRect(w * .03f, h * DESK, w * .97f, h * (DESK + .03f), p); p.shader = null
        p.color = a(A, .8f); c.drawRect(w * .03f, h * (DESK + .03f) - 2, w * .97f, h * (DESK + .03f), p)
        p.color = pal(0xFF141827, 0xFF9C8261); c.drawRect(w * .06f, h * (DESK + .03f), w * .09f, h * BACK, p); c.drawRect(w * .91f, h * (DESK + .03f), w * .94f, h * BACK, p)
        rr(c, w * .33f, h * (DESK - .012f), w * .34f, h * .016f, w * .008f, 0xFF1B2034.toInt())
        for (k in 0 until 14) { p.color = a(if (k % 4 != 0) A else 0xFFFF6EC7.toInt(), .3f + .25f * sin(t / 260f + k)); c.drawRect(w * (.345f + k * .022f), h * (DESK - .008f), w * (.361f + k * .022f), h * DESK, p) }
        rr(c, w * .71f, h * (DESK - .01f), w * .035f, h * .012f, w * .01f, 0xFF1B2034.toInt())
        val mugA = mAng(4, now)
        if (abs(mugA) > .6f) oval(c, w * .15f + Math.signum(mugA) * w * .05f, h * (DESK - .002f), w * .045f * min(1f, abs(mugA)), h * .004f, 0x8C6E3C1E.toInt())
        c.save(); c.rotate(mugA * 57.3f, w * (if (mugA > 0) .18f else .12f), h * DESK)
        rr(c, w * .12f, h * (DESK - .035f), w * .06f, h * .035f, w * .01f, pal(0xFF2A2F48, 0xFFFFFFFF)); c.restore()
        if (abs(mugA) < .2f) for (k in 0..1) { val pp = ((t / 1200f) + k / 2f) % 1f; circle(c, w * (.15f + sin(pp * 6) * .01f), h * (DESK - .04f - pp * .04f), w * .01f * (1 + pp), a(if (light) 0xFF6E7387.toInt() else Color.WHITE, .22f * (1 - pp))) }
        // floor + rug + glow
        p.shader = LinearGradient(0f, h * BACK, 0f, h, pal(0xFF0F1220, 0xFFD6DBE4), pal(0xFF06070D, 0xFFC2C8D4), Shader.TileMode.CLAMP); c.drawRect(0f, h * BACK, w, h, p); p.shader = null
        oval(c, w * .5f, h * .86f, w * .46f, h * .07f, pal(0xFF161A2E, 0xFFB8C0D0)); sp.color = a(A, .25f); sp.strokeWidth = 2f; r.set(w * .08f, h * .802f, w * .92f, h * .918f); c.drawOval(r, sp)

        // PC tower with RGB fans
        val TX = w * .8f; val TY = h * .63f; val TW = w * .16f; val TH = h * .2f
        oval(c, TX + TW / 2, TY + TH, TW * .6f, h * .008f, a(Color.BLACK, .25f)); rr(c, TX, TY, TW, TH, w * .01f, pal(0xFF11141F, 0xFFE6E9F0))
        for (k in 0..2) {
            val fx = TX + TW * .5f; val fy = TY + TH * (.2f + k * .3f); val fr = TW * .28f; val col = Color.HSVToColor(floatArrayOf((t / 20f + k * 80) % 360f, .85f, 1f))
            sp.color = col; sp.strokeWidth = w * .006f; c.drawCircle(fx, fy, fr, sp)
            c.save(); c.translate(fx, fy); c.rotate(t / 90f * 57.3f % 360f); p.color = a(col, .35f)
            for (b in 0 until 5) { c.rotate(72f); r.set(0f, -fr * .16f, fr * .9f, fr * .16f); c.drawOval(r, p) }
            c.restore()
        }
        sp.color = pal(0xFF0C0E17, 0xFF8C93A6); sp.strokeWidth = w * .008f
        path.reset(); path.moveTo(TX, TY + TH * .2f); path.quadTo(w * .72f, h * .7f, w * .6f, h * (DESK + .03f)); c.drawPath(path, sp)
        // ===== Jarvis in his chair =====
        var turn = 0f
        if (kind != null) turn = when { st in TURN1 until TYPE -> ease(ph(TURN1, TYPE)); st in TYPE until TURN2 -> 1f; st in TURN2 until TALK -> 1 - ease(ph(TURN2, TALK)); else -> 0f }
        val facingBack = turn > .5f; val sx = max(.05f, abs(cos(turn * PI.toFloat())))
        val S = w * .3f; val pinOn = kind == null && now < attentionUntil
        val bumpA = max(0f, 1 - (now - bumpAt) / 400f); val shakeA = max(0f, 1 - (now - shakeAt) / 1200f)
        val chX = w * .5f + sin(t / 35f) * w * .02f * shakeA + tiltX * w * .015f
        val chY = h * .73f + sin(t / 700f) * S * .015f - sin(bumpA * PI.toFloat()) * S * .08f + (if (pinOn) S * .04f else 0f)
        val fixI = if (messing && mt >= M_STAND && mt < M_SIT) ((mt - M_STAND) / M_STEP).toInt() else -1
        var flyX = chX; var flyY = chY; val flying = messing && mt >= M_SHAKE && mt < M_SIT + 600
        if (flying) {
            fun wp(i: Int) = if (i == 0) floatArrayOf(chX, chY) else { val it = FIX_P[i - 1]; floatArrayOf((it[0] * w + (if (it[0] < .5f) S * .62f else -S * .62f)).coerceIn(S * .75f, w - S * .75f), it[1] * h + S * .32f) }
            fun go(a: FloatArray, b: FloatArray, q: Float) { val e = ease(q.coerceIn(0f, 1f)); flyX = a[0] + (b[0] - a[0]) * e; flyY = a[1] + (b[1] - a[1]) * e - sin(e * PI.toFloat()) * S * .25f }
            when {
                mt < M_STAND -> go(wp(0), wp(1), (mt - M_SHAKE) / (M_STAND - M_SHAKE).toFloat())
                mt >= M_SIT -> go(wp(5), wp(0), (mt - M_SIT) / 600f)
                fixI > 0 -> go(wp(fixI), wp(fixI + 1), (mt - M_STAND - fixI * M_STEP) / 350f)
                else -> { val p1 = wp(1); flyX = p1[0]; flyY = p1[1] }
            }
            flyY += sin(t / 180f) * S * .04f
        }
        val cx = flyX
        val cy = flyY; val B = body; val shade = darker(B, .82f)
        if (now > nextLook) { lookTx = (Math.random() * 2 - 1).toFloat(); lookTy = (Math.random() * 1.2 - .2).toFloat(); nextLook = now + 1200 + (Math.random() * 2200).toLong() }
        var lx = lookTx; var ly = lookTy; var eyeScale = 1f; var mood = "idle"
        if (kind == null && sin(t / 3000f) > .3f) { lx = .55f; ly = .9f; mood = "phone" }
        val tAge = now - tapAt; val hyy = cy - S * .62f
        val poking = kind == null && pokeLine != null && now - pokeAt < 2600
        if (kind == null && tAge < 1800) { lx = ((tapX * w - cx) / (S * 1.1f)).coerceIn(-1f, 1f); ly = ((tapY * h - hyy) / (S * 1.1f)).coerceIn(-1f, 1f); mood = "idle"; if (tAge < 220) eyeScale = 1.12f }
        if (kind == null && tAge >= 1800 && (abs(tiltX) > .05f || abs(tiltY) > .05f)) { lx = tiltX; ly = tiltY; mood = "idle" }
        if (kind == null && now < coverUntil) mood = "squint"
        if (kind == null && now - pickAt < 700) eyeScale = 1.25f
        if (poking) mood = pokeMood
        if (messing) { when {
            mt < M_SHAKE -> { mood = "squint"; lx = sin(t / 60f); ly = cos(t / 70f) * .5f }
            fixI >= 0 -> { mood = "focus"; lx = ((FIX_P[fixI][0] * w - cx) / (S * .8f)).coerceIn(-1f, 1f); ly = -1f }
            mt >= M_SIT -> mood = "happy"
            else -> mood = "evil" } }
        if (pinOn) { lx = sin(t / 380f) * .45f; ly = 1f; eyeScale = 1.15f + .08f * max(0f, sin(t / 160f)); mood = "focus" }
        if (kind != null) { if (st < FLASH + 200) { lx = 0f; ly = 0f; eyeScale = 1.2f; mood = "focus" } else if (st >= TALK) { lx = 0f; ly = .1f; mood = if (intr) "evil" else if (kind == "noface") "squint" else "happy" } }
        lookX += (lx - lookX) * .15f; lookY += (ly - lookY) * .15f
        // chair base
        p.color = pal(0xFF0C0E17, 0xFF4A5068); c.drawRect(chX - w * .008f, chY + S * .35f, chX + w * .008f, chY + S * .7f, p)
        for (k in floatArrayOf(-1f, -.4f, .4f, 1f)) c.drawCircle(chX + k * S * .45f, chY + S * .72f, S * .045f, p)
        sp.color = pal(0xFF0C0E17, 0xFF4A5068); sp.strokeWidth = w * .01f; c.drawLine(chX - S * .45f, chY + S * .7f, chX + S * .45f, chY + S * .7f, sp)
        fun chairBack() { c.save(); c.translate(chX, 0f); c.scale(sx, 1f); rr(c, -S * .62f, chY - S * .85f, S * 1.24f, S * 1.15f, S * .25f, pal(0xFF1A1E33, 0xFF2A2F48)); rr(c, -S * .5f, chY - S * .75f, S * 1f, S * .95f, S * .2f, pal(0xFF232845, 0xFF3B4369))
            p.color = a(A, .8f); c.drawRect(-S * .5f, chY - S * .3f, -S * .46f, chY + S * .15f, p); c.drawRect(S * .46f, chY - S * .3f, S * .5f, chY + S * .15f, p); c.restore() }
        fun seat() = rr(c, chX - S * .6f * sx, chY + S * .22f, S * 1.2f * sx, S * .16f, S * .08f, pal(0xFF1A1E33, 0xFF2A2F48))
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
            if (flying) {
                for (sd in floatArrayOf(-1f, 1f)) { val jx = cx + sd * S * .46f; val jy = cy + S * .12f
                    rr(c, jx - S * .09f, jy - S * .2f, S * .18f, S * .36f, S * .06f, 0xFF3A4166.toInt()); p.color = A; c.drawRect(jx - S * .05f, jy - S * .12f, jx + S * .05f, jy - S * .09f, p)
                    val fl = S * (.38f + .14f * sin(t / 25f + sd) + (Math.random() * .08).toFloat())
                    p.shader = LinearGradient(0f, jy + S * .16f, 0f, jy + S * .16f + fl, intArrayOf(a(Color.WHITE, .95f), a(0xFFFFD25A.toInt(), .95f), a(0xFFFF5A28.toInt(), .7f), a(0xFFFF3C1E.toInt(), 0f)), floatArrayOf(0f, .25f, .7f, 1f), Shader.TileMode.CLAMP)
                    path.reset(); path.moveTo(jx - S * .07f, jy + S * .16f); path.quadTo(jx, jy + S * .16f + fl * 1.3f, jx + S * .07f, jy + S * .16f); path.close(); c.drawPath(path, p); p.shader = null }
                oval(c, cx, cy + S * .9f, S * .5f, S * .12f, a(0xFFFFB84D.toInt(), .12f))
            }
            c.save(); c.translate(cx, 0f); c.scale(sx, 1f)
            oval(c, 0f, bodyY + S * .06f, S * .48f, S * .4f, shade); oval(c, 0f, bodyY + S * .02f, S * .44f, S * .37f, B); oval(c, 0f, bodyY + S * .06f, S * .22f, S * .16f, 0xFF0B0E1A.toInt())
            sp.color = A; sp.strokeWidth = S * .02f; path.reset()
            for (k in 0..18) { val x = -S * .17f + S * .34f * k / 18; val y = bodyY + S * .06f + sin(k * .9f + t / 110f) * S * .05f * (if (kind != null) 1f else .3f); if (k == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            c.drawPath(path, sp)
            sp.color = shade; sp.strokeWidth = S * .1f
            for (sd in floatArrayOf(-1f, 1f)) { c.drawLine(sd * S * .18f, bodyY + S * .3f, sd * S * .2f, bodyY + S * .52f + sin(t / 500f + sd) * S * .02f, sp); oval(c, sd * S * .22f, bodyY + S * .56f, S * .1f, S * .05f, shade) }
            c.restore()
            seat()
            val raise = if (kind == null) 0f else when { st < AIM -> 1 - (1 - ph(0, AIM)).pow(3); st < TURN1 - 300 -> 1f; else -> 1 - ease(ph(TURN1 - 300, TURN1 + 100)) }
            if (kind != null && st < AIM + 80) { sp.color = a(A, .35f); sp.strokeWidth = S * .05f; for (k in 1..3) c.drawLine(cx + S * .3f + k * S * .04f, bodyY - k * S * .05f, cx + S * .2f + k * S * .04f, hy + S * .25f - k * S * .03f, sp) }
            val idleX = cx + S * .28f * sx; val idleY = bodyY + S * .02f + (if (mood == "phone") sin(t / 600f) * S * .02f else 0f)
            val aimX = cx + S * .18f * sx; val aimY = hy + S * .12f
            var hRx = idleX + (aimX - idleX) * raise; var hRy = idleY + (aimY - idleY) * raise
            val waving = kind == "owner" && st >= TALK && st < TALK + 2600
            if (waving) { hRx = cx + S * .62f + sin(t / 110f) * S * .1f; hRy = hy - S * .15f }
            if (fixI >= 0) { val it = FIX_P[fixI]; val ls = mt - M_STAND - fixI * M_STEP; val q = ease(((ls - 330) / 150f).coerceIn(0f, 1f)) * (1 - ease(((ls - 730) / 70f).coerceIn(0f, 1f)))
                val tx = it[0] * w + (if (it[0] < .5f) S * .15f else -S * .15f); val ty = it[1] * h + sin(t / 60f) * S * .03f * (if (ls > 400) 1f else 0f)
                hRx += (tx - hRx) * q; hRy += (ty - hRy) * q }
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
                if (mood == "squint") { oval(c, ex, ey, er, er * .32f, 0xFFF4F7FF.toInt()); circle(c, ex + sin(t / 300f) * er * .3f, ey, er * .28f, A); continue }
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
            if (waving || fixI >= 0) { } else if (raise > .5f) phone(hRx, hRy - pw * .6f, pw, back = true, scroll = false) else phone(hRx - S * .02f, hRy - pw * .5f, pw, back = false, scroll = true)
            if (pinOn) text(c, "👀", cx + S * .75f, hy - S * .4f, S * .2f, a(Color.WHITE, .6f + .4f * sin(t / 200f)))
            if (kind != null && st < 600) text(c, "!", cx + S * .78f, hy - S * .45f - sin(ph(0, 600) * PI.toFloat()) * S * .1f, S * .3f, if (intr) 0xFFFF3B5C.toInt() else 0xFFFFD166.toInt(), bold)
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
        c.restore()
        // effects
        if (alert > 0) { p.color = a(0xFFFF2846.toInt(), .12f * alert); c.drawRect(0f, 0f, w, h, p) }
        if (kind != null && st in FLASH until FLASH + 350) { val f = 1 - ph(FLASH, FLASH + 350); p.color = a(Color.WHITE, .92f * f); c.drawRect(0f, 0f, w, h, p); text(c, "CLICK!", w * .5f, h * .9f, w * .08f, a(0xFF0B0E1A.toInt(), f), bold) }
        // tap ripples
        var ri = 0
        while (ri < ripples.size) { val age = now - rippleT[ri]; if (age > 600) { ripples.removeAt(ri); rippleT.removeAt(ri); continue }
            val q = age / 600f; sp.color = a(A, .7f * (1 - q)); sp.strokeWidth = 2f; c.drawCircle(ripples[ri][0] * w, ripples[ri][1] * h, w * .02f + q * w * .07f, sp); ri++ }
        val say = if (kind == null) { if (pinOn) (if (now - attentionAt < 2500) "Hmm… someone's unlocking 👀" else "Let's see what you type… 🧐") else if (poking) pokeLine else null } else when {
            st < FLASH + 60 -> null
            st < TURN1 + 300 -> if (kind == "intruder") "Gotcha! Let's see who you are 🔍" else "Let's see who you are… 🔍"
            st in TALK until END -> when (kind) { "intruder" -> msgIntruder; "owner" -> msgOwner; "stranger" -> msgStranger; "noface" -> msgNoFace; else -> msgWake }
            else -> null }
        val say2 = if (messing && kind == null) when { mt < M_SHAKE -> messLine; mt < M_STAND -> "Ugh… my setup! Booster on 🚀"; fixI >= 0 -> FIX_LINE[fixI]; mt < M_END -> "There. All fixed 😤"; else -> null } else say
        sounds(kind, st, intr, say2, mt, fixI)
        if (say2 != null) { val say = say2
            tp.textSize = w * .04f; tp.typeface = bold; val tw = tp.measureText(say); val pad = w * .03f; val bw = min(w * .92f, tw + pad * 2); val bh = w * .04f + pad * 1.4f
            val bx = (cx - bw / 2).coerceIn(w * .04f, w * .96f - bw); val by = max(h * .035f, hy - S * .6f - bh)
            rr(c, bx, by, bw, bh, bh / 2, a(Color.WHITE, .96f))
            path.reset(); path.moveTo(cx - w * .02f, by + bh - 1); path.lineTo(cx, by + bh + w * .025f); path.lineTo(cx + w * .02f, by + bh - 1); path.close(); p.color = a(Color.WHITE, .96f); c.drawPath(path, p)
            text(c, say, bx + bw / 2, by + bh / 2 + w * .014f, w * .04f, 0xFF0B0E1A.toInt(), bold)
        }
    }

    private fun sounds(kind: String?, st: Long, intr: Boolean, say: String?, mt: Long, fixI: Int) {
        val fx = onSfx
        if (fx != null) {
            if (kind != null) {
                if (sndSeqStart != seqStart) { sndSeqStart = seqStart; sndShut = false; sndEnd = false; sndKey = -1 }
                if (st >= FLASH && !sndShut) { sndShut = true; fx("shutter") }
                if (st in TYPE until UPLOAD) { val k = ((st - TYPE) / 95).toInt(); if (k != sndKey) { sndKey = k; if (Math.random() < .8) fx("key") } }
                if (st >= TALK && !sndEnd) { sndEnd = true; if (intr) fx("alarm") else if (kind == "owner") fx("chirp") else if (kind == "noface") fx("hmm") }
            }
            if (mt < M_END) {
                if (mt >= M_SHAKE && !messDone[5]) { messDone[5] = true; fx("jet") }
                if (fixI >= 0 && mt - M_STAND - fixI * M_STEP > 500 && !messDone[fixI]) { messDone[fixI] = true; fx("fix") }
                if (mt >= M_SIT && !messDone[6]) { messDone[6] = true; fx("land") }
            }
        }
        if (say != lastSay) { if (say != null) onSay?.invoke(say); lastSay = say }
    }

    // ===== window with the real outdoor world =====
    private class Sun(val elev: Float, val x: Float, val nightX: Float)
    private fun sun(now: Long): Sun {
        val rad = PI / 180; val cal = java.util.Calendar.getInstance(); val n = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val decl = 23.44 * sin(2 * PI * (284 + n) / 365) * rad; val B = 2 * PI * (n - 81) / 364
        val eot = 9.87 * sin(2 * B) - 7.53 * cos(B) - 1.5 * sin(B)
        val utcMin = (now % 86_400_000L) / 60_000.0
        val solar = utcMin + 4 * lon + eot; val H = (solar / 4 - 180) * rad; val la = lat * rad
        val elev = Math.asin(sin(la) * sin(decl) + cos(la) * cos(decl) * cos(H)) / rad
        val H0 = Math.acos((-Math.tan(la) * Math.tan(decl)).coerceIn(-1.0, 1.0)) / rad
        val hh = ((H / rad + 540) % 360) - 180
        return Sun(elev.toFloat(), (.5 + hh / (2 * H0)).toFloat(), ((if (hh > 0) hh - H0 else hh + 360 - H0) / (360 - 2 * H0)).toFloat())
    }
    private val SKY_E = floatArrayOf(-90f, -10f, -3f, 3f, 10f, 90f)
    private val SKY_C = arrayOf(longArrayOf(0xFF060A1F, 0xFF141235, 0xFF25183E), longArrayOf(0xFF0B1030, 0xFF2A1748, 0xFF5A2350), longArrayOf(0xFF1D2A6B, 0xFF7A4A8A, 0xFFFF8A6B),
        longArrayOf(0xFF3D6FD1, 0xFFF29A7A, 0xFFFFC56B), longArrayOf(0xFF4FA6F5, 0xFFA9D6FF, 0xFFFFE7C4), longArrayOf(0xFF3C9BFF, 0xFF9FD2FF, 0xFFE4F3FF))
    private fun sky(e: Float): IntArray { for (i in 0 until SKY_E.size - 1) if (e <= SKY_E[i + 1]) { val k = (e - SKY_E[i]) / (SKY_E[i + 1] - SKY_E[i]); return IntArray(3) { mix(SKY_C[i][it].toInt(), SKY_C[i + 1][it].toInt(), k) } }
        return IntArray(3) { SKY_C[5][it].toInt() } }
    private val farH = FloatArray(14).also { val r = java.util.Random(4); for (k in it.indices) it[k] = .2f + r.nextFloat() * .3f }
    private val nearH = FloatArray(8).also { val r = java.util.Random(7); for (k in it.indices) it[k] = .25f + r.nextFloat() * .35f }
    private val stars = FloatArray(80).also { val r = java.util.Random(9); for (k in it.indices) it[k] = r.nextFloat() }

    private fun drawWindow(c: Canvas, w: Float, h: Float, t: Float, now: Long) {
        val SUN = sun(now); val EL = SUN.elev; val night = EL < -4
        val dull = when (weather) { "Rain", "Storm", "Snow" -> .55f; "Fog" -> .35f; else -> if (weather == "Cloudy") .15f else 0f }
        val WX = w * .04f; val WY = h * .05f; val WW = w * .42f; val WH = h * .21f
        val frame = pal(0xFF05060B, 0xFFC3C9D6)
        rr(c, WX - w * .02f, WY - w * .02f, WW + w * .04f, WH + w * .04f, w * .014f, frame)
        c.save(); c.clipRect(WX, WY, WX + WW, WY + WH)
        var SK = sky(EL); if (dull > 0) SK = IntArray(3) { mix(SK[it], if (night) 0xFF1A1C26.toInt() else 0xFF9AA1AD.toInt(), dull) }
        p.shader = LinearGradient(0f, WY, 0f, WY + WH, SK, floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP); c.drawRect(WX, WY, WX + WW, WY + WH, p); p.shader = null
        val starA = ((-EL - 4) / 8).coerceIn(0f, 1f) * (1 - dull * 1.6f).coerceAtLeast(0f)
        if (starA > 0) for (k in 0 until 40) { p.color = a(Color.WHITE, starA * (.3f + .7f * abs(sin(t / (600f + k * 37) + k)))); val x = WX + stars[k * 2] * WW; val y = WY + stars[k * 2 + 1] * WH * .55f; c.drawRect(x, y, x + 2.5f, y + 2.5f, p) }
        // moon with today's phase
        if (EL < 2 && dull < .5f) {
            val nx = SUN.nightX.coerceIn(0f, 1f); val mx = WX + WW * nx; val my = WY + WH * (.55f - .4f * sin(nx * PI.toFloat())); val mr = WW * .05f
            val ph = ((((now / 86_400_000.0 + 2440587.5) - 2451550.1) / 29.530588853) % 1.0 + 1.0) % 1.0
            val al = ((2 - EL) / 6).coerceIn(0f, 1f)
            circle(c, mx, my, mr, a(0xFF2A2C3E.toInt(), al))
            val k = cos(ph * 2 * PI).toFloat(); val wax = ph < .5
            path.reset(); r.set(mx - mr, my - mr, mx + mr, my + mr)
            path.arcTo(r, -90f, if (wax) 180f else -180f)
            r.set(mx - abs(k) * mr, my - mr, mx + abs(k) * mr, my + mr)
            path.arcTo(r, 90f, if (wax xor (k < 0)) -180f else 180f); path.close()
            p.color = a(0xFFFFF6D8.toInt(), al); c.drawPath(path, p)
        }
        // sun
        if (EL > -3) {
            val sx = WX + WW * SUN.x.coerceIn(0f, 1f); val sy = WY + WH * (.85f - (EL / 50).coerceIn(0f, 1f) * .7f); val low = (1 - EL / 12).coerceIn(0f, 1f); val al = 1 - dull
            p.shader = RadialGradient(sx, sy, WW * .2f, intArrayOf(a(Color.rgb(255, (240 - 80 * low).toInt(), (170 - 120 * low).toInt()), al), a(Color.rgb(255, (210 - 90 * low).toInt(), (120 - 90 * low).toInt()), .85f * al), a(0xFFFFC878.toInt(), 0f)), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(WX, WY, WX + WW, WY + WH, p); p.shader = null
        }
        // clouds
        val nc = when (weather) { "Clear" -> 2; "Cloudy" -> 5; else -> 7 }
        p.color = if (dull > .3f) (if (night) a(0xFF46485A.toInt(), .6f) else a(0xFFAAB0BC.toInt(), .9f)) else if (night) a(0xFF786EAA.toInt(), .25f) else a(Color.WHITE, .85f)
        for (k in 0 until nc) { val x0 = WX + ((t / (90f + k * 40) + k * WW * .45f) % (WW * 1.5f)) - WW * .25f; val y0 = WY + WH * (.12f + (k % 4) * .1f)
            c.drawCircle(x0, y0, .06f * WW, p); c.drawCircle(x0 + .05f * WW, y0 - .02f * WW, .07f * WW, p); c.drawCircle(x0 + .1f * WW, y0, .055f * WW, p); c.drawCircle(x0 + .05f * WW, y0 + .015f * WW, .06f * WW, p) }
        // random events
        if (evKind == null && now > evNext) {
            val pool = if (dull > .3f) listOf("plane", "heli") else if (night) listOf("plane", "star", "fireworks", "ufo", "plane", "star") else listOf("plane", "birds", "balloon", "birds", "plane", "heli")
            evKind = pool.random(); evStart = now; evY = .12f + (Math.random() * .3).toFloat(); evDir = if (Math.random() < .5) 1 else -1
        }
        evKind?.let { kind ->
            val dur = when (kind) { "plane" -> 7000; "star" -> 900; "fireworks" -> 2600; "ufo" -> 5000; "birds" -> 8000; "balloon" -> 14000; else -> 6000 }
            val q = (now - evStart) / dur.toFloat()
            if (q >= 1) { evKind = null; evNext = now + 3000 + (Math.random() * 7000).toLong() } else {
                val ex = if (evDir > 0) WX - WW * .1f + q * WW * 1.2f else WX + WW * 1.1f - q * WW * 1.2f; val ey = WY + WH * evY
                when (kind) {
                    "plane" -> { oval(c, ex, ey, WW * .035f, WW * .008f, if (night) 0xFF1A1F3A.toInt() else 0xFFF4F6FA.toInt()); p.color = if (night) 0xFF1A1F3A.toInt() else 0xFFF4F6FA.toInt(); c.drawRect(ex - WW * .008f, ey - WW * .02f, ex + WW * .004f, ey + WW * .02f, p)
                        circle(c, ex, ey, 3f, if ((t / 400).toInt() % 2 == 1) 0xFFFF3B5C.toInt() else 0xFF39FF9E.toInt())
                        if (!night) { sp.color = a(Color.WHITE, .7f); sp.strokeWidth = 3f; c.drawLine(ex - evDir * WW * .04f, ey, ex - evDir * WW * .35f, ey, sp) } }
                    "star" -> { val sx0 = WX + WW * (.2f + evY); val sy0 = WY + WH * .05f; val L = WW * .25f; sp.color = a(Color.WHITE, 1 - q); sp.strokeWidth = 3f; c.drawLine(sx0 + q * L, sy0 + q * L * .5f, sx0 + q * L - WW * .08f, sy0 + q * L * .5f - WW * .04f, sp) }
                    "fireworks" -> for (f in 0..1) { val fq = (q * 1.6f - f * .5f).coerceIn(0f, 1f); val fx = WX + WW * (.3f + f * .35f); val fy = WY + WH * (.25f + f * .08f); val col = if (f == 1) 0xFFFF5CC8.toInt() else 0xFFFFD166.toInt()
                        if (fq < .3f) { p.color = col; val yy = WY + WH * .7f - (WH * .7f - (fy - WY)) * fq / .3f; c.drawRect(fx, yy, fx + 3, yy + 6, p) }
                        else if (fq < 1f) { val rr2 = (fq - .3f) / .7f; for (k in 0 until 14) circle(c, fx + cos(k / 14f * 6.28f) * rr2 * WW * .1f, fy + sin(k / 14f * 6.28f) * rr2 * WW * .1f + rr2 * rr2 * WH * .05f, 3f, a(col, 1 - rr2)) } }
                    "ufo" -> { val uy = ey + sin(t / 200f) * WH * .03f; p.color = a(0xFF39FF9E.toInt(), .18f); path.reset(); path.moveTo(ex - WW * .02f, uy); path.lineTo(ex + WW * .02f, uy); path.lineTo(ex + WW * .06f, WY + WH); path.lineTo(ex - WW * .06f, WY + WH); path.close(); c.drawPath(path, p)
                        oval(c, ex, uy, WW * .05f, WW * .014f, 0xFF9AA3C0.toInt()); p.color = 0xFF39FF9E.toInt(); r.set(ex - WW * .022f, uy - WW * .024f, ex + WW * .022f, uy + WW * .004f); c.drawArc(r, 180f, 180f, true, p) }
                    "birds" -> { sp.color = 0xFF33405A.toInt(); sp.strokeWidth = 2.5f; for (b in 0..3) { val bx = ex - evDir * b * WW * .05f; val by = ey + (b % 2) * WH * .05f + b * WH * .02f; val fl = sin(t / 120f + b) * WW * .012f
                        path.reset(); path.moveTo(bx - WW * .018f, by - fl); path.quadTo(bx - WW * .008f, by - WW * .006f, bx, by); path.quadTo(bx + WW * .008f, by - WW * .006f, bx + WW * .018f, by - fl); c.drawPath(path, sp) } }
                    "balloon" -> { val by = ey + sin(t / 900f) * WH * .04f; circle(c, ex, by, WW * .045f, 0xFFFF6B6B.toInt()); p.color = 0xFFFFD166.toInt(); c.drawRect(ex - WW * .012f, by - WW * .045f, ex + WW * .012f, by + WW * .045f, p)
                        sp.color = 0xFF6B4A2A.toInt(); sp.strokeWidth = 1.5f; c.drawLine(ex - WW * .025f, by + WW * .035f, ex - WW * .012f, by + WW * .075f, sp); c.drawLine(ex + WW * .025f, by + WW * .035f, ex + WW * .012f, by + WW * .075f, sp)
                        p.color = 0xFF8A5A2A.toInt(); c.drawRect(ex - WW * .014f, by + WW * .075f, ex + WW * .014f, by + WW * .095f, p) }
                    else -> { oval(c, ex, ey, WW * .03f, WW * .014f, 0xFF2A3550.toInt()); p.color = 0xFF2A3550.toInt(); c.drawRect(min(ex, ex - evDir * WW * .06f), ey - 2, max(ex, ex - evDir * WW * .06f), ey + 2, p)
                        sp.color = 0xFF2A3550.toInt(); sp.strokeWidth = 2.5f; val rw = WW * .045f * abs(sin(t / 30f)); c.drawLine(ex - rw, ey - WW * .02f, ex + rw, ey - WW * .02f, sp) }
                }
            }
        }
        // skyline
        val dayK = ((EL + 4) / 12).coerceIn(0f, 1f)
        p.color = mix(0xFF1B1640.toInt(), 0xFF9FB3D1.toInt(), dayK)
        for (k in 0 until 14) { val bh = WH * farH[k]; c.drawRect(WX + WW * k / 14, WY + WH - bh, WX + WW * k / 14 + WW / 14 * .9f, WY + WH, p) }
        for (k in 0 until 8) {
            val bx = WX + WW * k / 8; val bw = WW / 8 * .86f; val bh = WH * nearH[k]
            p.color = mix(0xFF0B0A1C.toInt(), 0xFF6F84A8.toInt(), dayK); c.drawRect(bx, WY + WH - bh, bx + bw, WY + WH, p)
            for (j in 0 until 10) {
                val on = EL < 3 && sin(t / 4000f + k * 3 + j * 7) > -.3f + (EL.coerceIn(0f, 3f) / 3) * 1.2f
                p.color = if (on) (if (j % 3 != 0) 0xFFFFD68C.toInt() else 0xFF3EE8FF.toInt()) else if (dayK < .5f) 0xFF15142A.toInt() else a(0xFFDCEBFF.toInt(), .6f)
                val lx = bx + bw * .18f + (j % 2) * bw * .4f; val ly = WY + WH - bh + bh * .08f + (j / 2) * bh * .17f; c.drawRect(lx, ly, lx + bw * .22f, ly + bh * .07f, p)
            }
            if (k == 3) circle(c, bx + bw / 2, WY + WH - bh - 4, 3f, if ((t / 600).toInt() % 2 == 1 && night) 0xFFFF3B5C.toInt() else 0xFF55607A.toInt())
        }
        if (weather == "Fog") { p.color = if (night) a(0xFF5A5F6E.toInt(), .45f) else a(0xFFE6E9EE.toInt(), .6f); c.drawRect(WX, WY + WH * .3f, WX + WW, WY + WH, p) }
        p.color = if (night) a(Color.WHITE, .05f) else a(Color.WHITE, .22f)
        path.reset(); path.moveTo(WX + WW * .1f, WY); path.lineTo(WX + WW * .24f, WY); path.lineTo(WX + WW * .06f, WY + WH); path.lineTo(WX - WW * .08f, WY + WH); path.close(); c.drawPath(path, p)
        c.restore()
        // panes, blind, sill, plant, light on the wall
        p.color = frame; c.drawRect(WX + WW / 2 - w * .006f, WY, WX + WW / 2 + w * .006f, WY + WH, p); c.drawRect(WX, WY + WH * .42f - w * .005f, WX + WW, WY + WH * .42f + w * .005f, p)
        val trim = pal(0xFF2A2F48, 0xFFE7E9EF)
        rr(c, WX - w * .025f, WY - w * .03f, WW + w * .05f, h * .016f, w * .006f, trim); p.color = pal(0xFF1D2236, 0xFFCFD4DE); c.drawRect(WX + WW * .48f, WY - w * .015f, WX + WW * .48f + w * .004f, WY - w * .015f + h * .03f, p)
        rr(c, WX - w * .03f, WY + WH + w * .012f, WW + w * .06f, h * .012f, w * .004f, trim)
        val potY = WY + WH + w * .012f
        rr(c, WX + WW * .08f, potY - h * .022f, w * .04f, h * .022f, 3f, 0xFFC76B4A.toInt())
        for (k in -2..2) { c.save(); c.rotate((k * .35f + sin(t / 1200f) * .05f) * 57.3f, WX + WW * .08f + w * .02f + k * w * .008f, potY - h * .03f); oval(c, WX + WW * .08f + w * .02f + k * w * .008f, potY - h * .03f, w * .006f, w * .016f, 0xFF3FAE6A.toInt()); c.restore() }
        p.color = if (night) a(0xFF9B6BFF.toInt(), .05f) else a(0xFFFFF2C4.toInt(), .12f)
        path.reset(); path.moveTo(WX + WW, WY); path.lineTo(WX + WW + w * .3f, WY + h * .12f); path.lineTo(WX + WW + w * .3f, WY + h * .12f + WH * .8f); path.lineTo(WX + WW, WY + WH); path.close(); c.drawPath(path, p)
    }
}
