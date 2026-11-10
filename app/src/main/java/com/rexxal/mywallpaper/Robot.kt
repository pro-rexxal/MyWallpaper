package com.rexxal.mywallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A small procedural robot that lives on the live wallpaper.
 * Positions are 0..1 of the screen so it works at any size.
 */
class Robot {

    companion object {
        val THOUGHTS = listOf(
            "You're doing great today ✨",
            "Drink some water 💧",
            "Small steps still count.",
            "Take a deep breath…",
            "You've got this! 💪",
            "Be kind to yourself.",
            "Rest is productive too.",
            "Today is a fresh start.",
            "Proud of you!",
            "One thing at a time.",
            "Stretch a little? 🙆",
            "Your future self says thanks.",
            "Beep boop… I like you 🤖",
            "Mistakes mean you're trying.",
            "Go outside for a minute ☀️"
        )
        private val GIGGLES = listOf("Hehe! 😄", "Boop!", "That tickles!", "Wheee!", "Hi there! 👋", "Beep beep!")
        private const val SLEEP_AFTER_MS = 25_000L
    }

    // position & movement (0..1)
    var x = 0.5f; private set
    var y = 0.78f; private set
    private var tx = 0.5f
    private var ty = 0.78f
    private var facing = 1f

    // looking
    private var lookX = 0f
    private var lookY = 0f
    private var lookTx = 0f
    private var lookTy = 0f
    private var nextLook = 0L

    // state
    var sleeping = false; private set
    private var lastInteract = System.currentTimeMillis()
    private var bounceStart = -10_000L
    private var happyUntil = 0L
    private var waveUntil = 0L
    private var speech: String? = null
    private var speechUntil = 0L
    private var nextThought = System.currentTimeMillis() + 20_000L
    private var nextWander = System.currentTimeMillis() + 8_000L
    private var lastTap = 0L
    private var blinkStart = -1000L
    private var nextBlink = 0L
    private var lastUpdate = 0L
    private var sleepStart = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

    // ---------------- interaction ----------------

    fun say(text: String, now: Long, ms: Long = 3500L) {
        speech = text
        speechUntil = now + ms
    }

    /** nx, ny = touch position 0..1; size = robot size as a fraction of screen width; aspect = w/h */
    fun onTap(nx: Float, ny: Float, now: Long, size: Float, aspect: Float) {
        lookAt(nx, ny)
        val onRobot = abs(nx - x) < size * 0.6f && abs(ny - y) < size * aspect * 0.9f
        val doubleTap = now - lastTap < 380L
        lastTap = now
        lastInteract = now

        if (sleeping) {
            sleeping = false
            bounceStart = now
            say(if (Random.nextBoolean()) "Yawn… I'm up! 🥱" else "Huh? Oh, hi! 👋", now)
            return
        }
        if (doubleTap) {
            say("It's " + timeText(now) + " ⏰", now, 4000L)
            happyUntil = now + 1500L
            bounceStart = now
            return
        }
        if (onRobot) {
            bounceStart = now
            happyUntil = now + 1200L
            if (Random.nextInt(3) == 0) say(GIGGLES.random(), now, 1600L)
        } else {
            walkTo(nx, ny)
        }
    }

    fun onDrag(nx: Float, ny: Float, now: Long) {
        lastInteract = now
        if (sleeping) return
        walkTo(nx, ny)
        lookAt(nx, ny)
    }

    /** Phone unlocked / wallpaper visible again. */
    fun onReturn(now: Long) {
        if (now - lastInteract > 60_000L) {
            sleeping = false
            lastInteract = now
            waveUntil = now + 1500L
            say(greeting(now), now)
        }
    }

    private fun walkTo(nx: Float, ny: Float) {
        tx = nx.coerceIn(0.12f, 0.88f)
        ty = ny.coerceIn(0.35f, 0.9f)
    }

    private fun lookAt(nx: Float, ny: Float) {
        lookTx = ((nx - x) * 4f).coerceIn(-1f, 1f)
        lookTy = ((ny - y) * 4f).coerceIn(-1f, 1f)
        nextLook = System.currentTimeMillis() + 2500L
    }

    private fun timeText(now: Long) = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))

    private fun greeting(now: Long): String {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..11 -> "Good morning! ☀️"
            in 12..16 -> "Good afternoon! 🌤"
            in 17..21 -> "Good evening! 🌆"
            else -> "Up late? Sleep soon 🌙"
        }
    }

    // ---------------- simulation ----------------

    fun update(now: Long) {
        val dt = if (lastUpdate == 0L) 0f else ((now - lastUpdate).coerceIn(0L, 100L)) / 1000f
        lastUpdate = now

        if (!sleeping) {
            // walk
            val dx = tx - x
            val dy = ty - y
            val step = 0.22f * dt
            if (abs(dx) > 0.003f) {
                x += dx.coerceIn(-step, step)
                facing = if (dx > 0) 1f else -1f
            }
            if (abs(dy) > 0.003f) y += dy.coerceIn(-step, step)

            // fall asleep when ignored
            if (now - lastInteract > SLEEP_AFTER_MS && now > speechUntil) {
                sleeping = true
                sleepStart = now
                speech = null
            }
            // good thoughts
            if (now > nextThought) {
                if (now > speechUntil) say(THOUGHTS.random(), now, 4500L)
                nextThought = now + 35_000L + Random.nextLong(30_000L)
            }
            // idle wandering
            if (now > nextWander) {
                if (abs(tx - x) < 0.01f) tx = (x + (Random.nextFloat() - 0.5f) * 0.3f).coerceIn(0.15f, 0.85f)
                nextWander = now + 6_000L + Random.nextLong(8_000L)
            }
            // look around
            if (now > nextLook) {
                lookTx = Random.nextFloat() * 2f - 1f
                lookTy = Random.nextFloat() * 1.2f - 0.6f
                nextLook = now + 1500L + Random.nextLong(2500L)
            }
            // blink
            if (now > nextBlink) {
                blinkStart = now
                nextBlink = now + 2200L + Random.nextLong(3500L)
            }
        }
        val k = (dt * 8f).coerceAtMost(1f)
        lookX += (lookTx - lookX) * k
        lookY += (lookTy - lookY) * k
        if (speech != null && now > speechUntil) speech = null
    }

    // ---------------- drawing ----------------

    fun draw(c: Canvas, w: Float, h: Float, now: Long, body: Int, sizePct: Int) {
        update(now)
        val s = w * sizePct / 100f
        val walking = !sleeping && (abs(tx - x) > 0.004f || abs(ty - y) > 0.004f)
        val happy = now < happyUntil
        val waving = now < waveUntil

        val bob = if (sleeping) sin(now / 900f) * s * 0.02f else sin(now / 380f) * s * 0.05f
        val bt = (now - bounceStart) / 550f
        val bounce = if (bt in 0f..1f) -sin(bt * PI.toFloat()) * s * 0.45f else 0f
        val groundY = y * h
        val cx = x * w
        val cy = groundY - s * 0.55f + bob + bounce

        val shade = darker(body, 0.82f)
        val eye = if (abs(Color.blue(body) - 240) < 30 && Color.green(body) > 180 && Color.red(body) < 120) 0xFFFFD166.toInt() else 0xFF4CC9F0.toInt()

        // shadow
        val lift = ((-bounce) / (s * 0.45f)).coerceIn(0f, 1f)
        paint.shader = null
        paint.color = Color.argb((70 * (1f - lift * 0.6f)).toInt(), 0, 0, 0)
        rect.set(cx - s * 0.45f * (1f - lift * 0.3f), groundY + s * 0.02f, cx + s * 0.45f * (1f - lift * 0.3f), groundY + s * 0.14f)
        c.drawOval(rect, paint)

        c.save()
        if (walking) c.rotate(facing * 5f + sin(now / 120f) * 2f, cx, cy + s * 0.5f)

        // hover jet
        val jet = if (sleeping) 40 else 120 + (sin(now / 90f) * 40).toInt()
        paint.shader = RadialGradient(cx, cy + s * 0.62f, s * 0.3f,
            intArrayOf(withAlpha(eye, jet), withAlpha(eye, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy + s * 0.62f, s * 0.3f, paint)
        paint.shader = null

        // arms
        stroke.color = shade
        stroke.strokeWidth = s * 0.09f
        val swing = when {
            walking -> sin(now / 160f) * 20f
            happy -> 70f
            sleeping -> -10f
            else -> sin(now / 700f) * 6f
        }
        arm(c, cx - s * 0.42f, cy + s * 0.12f, -1f, if (walking) -swing else swing, s)
        arm(c, cx + s * 0.42f, cy + s * 0.12f, 1f, if (waving) 140f + sin(now / 90f) * 25f else swing, s)

        // body
        paint.color = shade
        rect.set(cx - s * 0.4f, cy - s * 0.02f, cx + s * 0.4f, cy + s * 0.55f)
        c.drawRoundRect(rect, s * 0.18f, s * 0.18f, paint)
        // chest light
        val beat = if (sleeping) 0.3f else 0.6f + 0.4f * sin(now / 300f)
        paint.color = withAlpha(eye, (255 * beat).toInt())
        c.drawCircle(cx, cy + s * 0.27f, s * 0.07f, paint)

        // antenna
        stroke.color = shade
        stroke.strokeWidth = s * 0.04f
        val antSway = sin(now / 500f) * s * 0.04f
        c.drawLine(cx, cy - s * 0.6f, cx + antSway, cy - s * 0.82f, stroke)
        val ballA = if (sleeping) 90 else (180 + 75 * sin(now / 250f)).toInt()
        paint.shader = RadialGradient(cx + antSway, cy - s * 0.86f, s * 0.16f,
            intArrayOf(withAlpha(0xFFFF5A5F.toInt(), ballA), withAlpha(0xFFFF5A5F.toInt(), 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx + antSway, cy - s * 0.86f, s * 0.16f, paint)
        paint.shader = null
        paint.color = withAlpha(0xFFFF5A5F.toInt(), 255)
        c.drawCircle(cx + antSway, cy - s * 0.86f, s * 0.06f, paint)

        // head
        paint.color = body
        rect.set(cx - s * 0.5f, cy - s * 0.62f, cx + s * 0.5f, cy + s * 0.04f)
        c.drawRoundRect(rect, s * 0.24f, s * 0.24f, paint)
        // ears
        paint.color = shade
        rect.set(cx - s * 0.58f, cy - s * 0.38f, cx - s * 0.46f, cy - s * 0.18f)
        c.drawRoundRect(rect, s * 0.05f, s * 0.05f, paint)
        rect.set(cx + s * 0.46f, cy - s * 0.38f, cx + s * 0.58f, cy - s * 0.18f)
        c.drawRoundRect(rect, s * 0.05f, s * 0.05f, paint)
        // face screen
        paint.color = 0xFF15171F.toInt()
        rect.set(cx - s * 0.4f, cy - s * 0.52f, cx + s * 0.4f, cy - s * 0.06f)
        c.drawRoundRect(rect, s * 0.17f, s * 0.17f, paint)

        // eyes
        val ex = lookX * s * 0.07f
        val ey = lookY * s * 0.04f
        val eyeY = cy - s * 0.31f + ey
        val bl = (now - blinkStart) / 160f
        val blink = if (bl in 0f..1f) abs(1f - bl * 2f).coerceAtLeast(0.1f) else 1f
        stroke.color = eye
        stroke.strokeWidth = s * 0.05f
        for (side in listOf(-1f, 1f)) {
            val px = cx + side * s * 0.17f + ex
            when {
                sleeping -> c.drawLine(px - s * 0.07f, eyeY + s * 0.02f, px + s * 0.07f, eyeY + s * 0.02f, stroke)
                happy -> {
                    path.reset()
                    path.moveTo(px - s * 0.07f, eyeY + s * 0.03f)
                    path.quadTo(px, eyeY - s * 0.08f, px + s * 0.07f, eyeY + s * 0.03f)
                    c.drawPath(path, stroke)
                }
                else -> {
                    paint.color = eye
                    val eh = s * 0.1f * blink
                    rect.set(px - s * 0.06f, eyeY - eh, px + s * 0.06f, eyeY + eh)
                    c.drawRoundRect(rect, s * 0.05f, s * 0.05f, paint)
                    paint.color = 0xCCFFFFFF.toInt()
                    if (blink > 0.6f) c.drawCircle(px + s * 0.025f, eyeY - s * 0.04f, s * 0.018f, paint)
                }
            }
        }
        // cheeks
        paint.color = 0x66FF6EC7
        c.drawCircle(cx - s * 0.29f + ex, cy - s * 0.17f, s * 0.04f, paint)
        c.drawCircle(cx + s * 0.29f + ex, cy - s * 0.17f, s * 0.04f, paint)
        // mouth
        stroke.strokeWidth = s * 0.03f
        path.reset()
        val my = cy - s * 0.15f
        if (sleeping) {
            paint.color = eye
            c.drawCircle(cx + ex, my, s * 0.025f, paint)
        } else {
            val open = if (happy || speech != null) s * 0.07f else s * 0.035f
            path.moveTo(cx - s * 0.06f + ex, my)
            path.quadTo(cx + ex, my + open, cx + s * 0.06f + ex, my)
            c.drawPath(path, stroke)
        }
        c.restore()

        // Zzz
        if (sleeping) {
            textPaint.color = 0xCCFFFFFF.toInt()
            for (i in 0..2) {
                val t = ((now - sleepStart) / 2400f + i / 3f) % 1f
                textPaint.textSize = s * (0.14f + t * 0.16f)
                textPaint.alpha = (255 * (1f - t)).toInt()
                c.drawText("z", cx + s * (0.35f + t * 0.35f), cy - s * (0.7f + t * 0.6f), textPaint)
            }
            textPaint.alpha = 255
        }

        // speech bubble
        val sp = speech
        if (sp != null) {
            textPaint.textSize = s * 0.2f
            textPaint.color = 0xFF15171F.toInt()
            val tw = textPaint.measureText(sp)
            val pad = s * 0.12f
            val bw = tw + pad * 2
            val bh = textPaint.textSize + pad * 1.4f
            var bx = cx - bw / 2
            bx = bx.coerceIn(w * 0.03f, w * 0.97f - bw)
            val by = cy - s * 1.05f - bh
            paint.color = 0xF2FFFFFF.toInt()
            rect.set(bx, by, bx + bw, by + bh)
            c.drawRoundRect(rect, bh / 2, bh / 2, paint)
            path.reset()
            val tail = cx.coerceIn(bx + bh / 2, bx + bw - bh / 2)
            path.moveTo(tail - s * 0.07f, by + bh - 1)
            path.lineTo(tail, by + bh + s * 0.1f)
            path.lineTo(tail + s * 0.07f, by + bh - 1)
            path.close()
            c.drawPath(path, paint)
            c.drawText(sp, bx + bw / 2, by + bh / 2 + textPaint.textSize * 0.35f, textPaint)
        }
    }

    /** angleDeg: 0 = hanging down, bigger = raised outward. */
    private fun arm(c: Canvas, sx: Float, sy: Float, side: Float, angleDeg: Float, s: Float) {
        val a = Math.toRadians((angleDeg + 15f).toDouble()).toFloat()
        val len = s * 0.3f
        val hx = sx + side * sin(a) * len
        val hy = sy + cos(a) * len
        c.drawLine(sx, sy, hx, hy, stroke)
        paint.color = stroke.color
        c.drawCircle(hx, hy, s * 0.06f, paint)
    }

    private fun darker(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
}
