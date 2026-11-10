package com.rexxal.mywallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
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
import kotlin.math.sin

/** Everything the live wallpaper can be customized with. */
data class LiveConfig(
    var bgMode: Int = 4,          // 0 photo, 1 gradient, 2 particles, 3 solid, 4 robot room
    var color1: Int = 0xFF7C5CFF.toInt(),
    var color2: Int = 0xFF1B1F3B.toInt(),
    var speed: Int = 4,           // 1..10
    var photoDim: Int = 25,       // 0..80 %
    var showTime: Boolean = true,
    var use24h: Boolean = false,
    var showSeconds: Boolean = false,
    var showAmPm: Boolean = false,
    var font: Int = 1,
    var timeSize: Int = 22,       // % of screen width
    var textColor: Int = 0xFFFFFFFF.toInt(),
    var shadow: Boolean = true,
    var showDate: Boolean = true,
    var dateFormat: Int = 0,
    var dateSize: Int = 5,        // % of screen width
    var dateAbove: Boolean = false,
    var posY: Int = 30,           // % from top
    var align: Int = 1,           // 0 left, 1 center, 2 right
    var robot: Boolean = true,
    var robotColor: Int = 0xFFFFFFFF.toInt(),
    var robotSize: Int = 18,      // % of screen width
    var sleepSec: Int = 25,
    var winMode: Int = 0,         // 0 real world, 1 dream world, 2 fixed scene
    var weatherSel: Int = 0,      // 0 live from your city, 1.. = WEATHERS[i-1]
    var city: String = "",
    var scene: Int = 3,
    var neonTheme: Int = 0,
    var lightsOn: Boolean = true,
    var roomShift: Int = 12,      // % the room is moved up
    var liveWeather: String = "Clear"
) {
    val animated get() = bgMode == 1 || bgMode == 2 || bgMode == 4 || robot

    fun save(p: SharedPreferences) {
        p.edit()
            .putInt("lv_bg", bgMode).putInt("lv_c1", color1).putInt("lv_c2", color2)
            .putInt("lv_speed", speed).putInt("lv_dim", photoDim)
            .putBoolean("lv_time", showTime).putBoolean("lv_24", use24h)
            .putBoolean("lv_sec", showSeconds).putBoolean("lv_ampm", showAmPm)
            .putInt("lv_font", font).putInt("lv_tsize", timeSize).putInt("lv_tcol", textColor)
            .putBoolean("lv_shadow", shadow).putBoolean("lv_date", showDate)
            .putInt("lv_dfmt", dateFormat).putInt("lv_dsize", dateSize).putBoolean("lv_dabove", dateAbove)
            .putInt("lv_y", posY).putInt("lv_align", align)
            .putBoolean("lv_robot", robot).putInt("lv_rcol", robotColor).putInt("lv_rsize", robotSize)
            .putInt("lv_sleep", sleepSec).putInt("lv_win", winMode).putInt("lv_wsel", weatherSel).putString("lv_city", city)
            .putInt("lv_scene", scene).putInt("lv_neon", neonTheme).putBoolean("lv_lights", lightsOn).putInt("lv_shift", roomShift)
            .apply()
    }

    companion object {
        fun load(p: SharedPreferences) = LiveConfig(
            bgMode = p.getInt("lv_bg", 4), color1 = p.getInt("lv_c1", 0xFF7C5CFF.toInt()),
            color2 = p.getInt("lv_c2", 0xFF1B1F3B.toInt()), speed = p.getInt("lv_speed", 4),
            photoDim = p.getInt("lv_dim", 25), showTime = p.getBoolean("lv_time", true),
            use24h = p.getBoolean("lv_24", false), showSeconds = p.getBoolean("lv_sec", false),
            showAmPm = p.getBoolean("lv_ampm", false), font = p.getInt("lv_font", 1),
            timeSize = p.getInt("lv_tsize", 22), textColor = p.getInt("lv_tcol", 0xFFFFFFFF.toInt()),
            shadow = p.getBoolean("lv_shadow", true), showDate = p.getBoolean("lv_date", true),
            dateFormat = p.getInt("lv_dfmt", 0), dateSize = p.getInt("lv_dsize", 5),
            dateAbove = p.getBoolean("lv_dabove", false),
            posY = p.getInt("lv_y", 30), align = p.getInt("lv_align", 1),
            robot = p.getBoolean("lv_robot", true), robotColor = p.getInt("lv_rcol", 0xFFFFFFFF.toInt()),
            robotSize = p.getInt("lv_rsize", 18),
            sleepSec = p.getInt("lv_sleep", 25), winMode = p.getInt("lv_win", 0), weatherSel = p.getInt("lv_wsel", 0),
            city = p.getString("lv_city", "") ?: "", scene = p.getInt("lv_scene", 3), neonTheme = p.getInt("lv_neon", 0),
            lightsOn = p.getBoolean("lv_lights", true), roomShift = p.getInt("lv_shift", 12), liveWeather = p.getString("wx_now", "Clear") ?: "Clear"
        )
    }
}

object LiveRenderer {

    val FONT_LABELS = listOf("Default", "Thin", "Light", "Bold", "Black", "Condensed", "Serif", "Mono")
    private val FONTS: List<Typeface> by lazy {
        listOf(
            Typeface.create("sans-serif", Typeface.NORMAL),
            Typeface.create("sans-serif-thin", Typeface.NORMAL),
            Typeface.create("sans-serif-light", Typeface.NORMAL),
            Typeface.create("sans-serif", Typeface.BOLD),
            Typeface.create("sans-serif-black", Typeface.NORMAL),
            Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            Typeface.SERIF,
            Typeface.MONOSPACE
        )
    }

    val DATE_PATTERNS = listOf("EEEE, d MMMM", "EEE, d MMM", "d MMMM yyyy", "dd/MM/yyyy", "MMMM d", "EEEE")
    val BG_LABELS = listOf("Photo", "Gradient", "Particles", "Solid", "Robot room")

    fun dateExample(i: Int): String = SimpleDateFormat(DATE_PATTERNS[i], Locale.getDefault()).format(Date())

    // Particles are computed from time, so nothing has to be stored between frames.
    private const val N = 70
    private val px = FloatArray(N)
    private val py = FloatArray(N)
    private val ps = FloatArray(N)
    private val pr = FloatArray(N)
    private val pp = FloatArray(N)
    init {
        val r = java.util.Random(7)
        for (i in 0 until N) {
            px[i] = r.nextFloat(); py[i] = r.nextFloat()
            ps[i] = 0.3f + r.nextFloat(); pr[i] = 0.4f + r.nextFloat() * 1.6f
            pp[i] = r.nextFloat() * 6.28f
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun photoFile(c: Context) = File(c.filesDir, "live_bg.jpg")

    fun loadPhoto(c: Context, maxW: Int): Bitmap? {
        val f = photoFile(c)
        if (!f.exists()) return null
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            var s = 1
            while (b.outWidth / (s * 2) >= maxW) s *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
        } catch (e: Throwable) {
            null
        }
    }

    fun draw(c: Canvas, w: Int, h: Int, now: Long, cfg: LiveConfig, photo: Bitmap?, world: RoomWorld? = null) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val t = AnimClock.sec(now) * (cfg.speed / 4f)
        world?.applyCfg(cfg)
        val finish = world?.draw(c, wf, hf, now, cfg, cfg.bgMode == 4)

        // ----- background -----
        when (cfg.bgMode) {
            4 -> {}
            0 -> {
                if (photo != null) {
                    val sc = max(wf / photo.width, hf / photo.height)
                    val dw = photo.width * sc
                    val dh = photo.height * sc
                    c.drawBitmap(photo, null, RectF((wf - dw) / 2, (hf - dh) / 2, (wf + dw) / 2, (hf + dh) / 2), bmpPaint)
                } else {
                    c.drawColor(cfg.color2)
                }
                if (cfg.photoDim > 0) c.drawColor(Color.argb(cfg.photoDim * 255 / 100, 0, 0, 0))
            }
            1 -> {
                val a = t * 0.15f
                val cx = wf / 2; val cy = hf / 2
                val r = max(wf, hf)
                fill.shader = LinearGradient(
                    cx + cos(a) * r, cy + sin(a) * r, cx - cos(a) * r, cy - sin(a) * r,
                    cfg.color1, cfg.color2, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, wf, hf, fill)
                // two soft moving glows
                for (k in 0..1) {
                    val gx = wf * (0.5f + 0.35f * sin(t * 0.21f + k * 2.1f))
                    val gy = hf * (0.5f + 0.35f * cos(t * 0.17f + k * 1.3f))
                    val gc = if (k == 0) cfg.color1 else lighten(cfg.color2)
                    fill.shader = RadialGradient(gx, gy, wf * 0.9f, intArrayOf(withAlpha(gc, 110), withAlpha(gc, 0)), null, Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, wf, hf, fill)
                }
                fill.shader = null
            }
            2 -> {
                fill.shader = LinearGradient(0f, 0f, 0f, hf, cfg.color2, darken(cfg.color2), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, wf, hf, fill)
                fill.shader = null
                val unit = wf / 200f
                for (i in 0 until N) {
                    var y = (py[i] - t * 0.02f * ps[i]) % 1f
                    if (y < 0) y += 1f
                    val x = px[i] + 0.03f * sin(t * 0.5f * ps[i] + pp[i])
                    val tw = 0.55f + 0.45f * sin(t * 1.3f * ps[i] + pp[i])
                    fill.color = withAlpha(cfg.color1, (200 * tw).toInt())
                    c.drawCircle(x * wf, y * hf, pr[i] * unit * 2.2f, fill)
                }
            }
            else -> c.drawColor(cfg.color2)
        }

        // ----- clock & date -----
        val date = Date(now)
        val margin = wf * 0.08f
        val align = when (cfg.align) { 0 -> Paint.Align.LEFT; 2 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
        val x = when (cfg.align) { 0 -> margin; 2 -> wf - margin; else -> wf / 2 }
        val tf = FONTS[cfg.font.coerceIn(0, FONTS.size - 1)]

        timePaint.typeface = tf
        timePaint.textAlign = align
        timePaint.color = cfg.textColor
        timePaint.textSize = wf * cfg.timeSize / 100f
        datePaint.typeface = tf
        datePaint.textAlign = align
        datePaint.color = cfg.textColor
        datePaint.textSize = wf * cfg.dateSize / 100f
        if (cfg.shadow && cfg.bgMode == 4 && world != null) {
            val g = (world.neon[0] and 0x00FFFFFF) or (0xC0 shl 24)
            timePaint.setShadowLayer(wf * 0.045f, 0f, 0f, g)
            datePaint.setShadowLayer(wf * 0.03f, 0f, 0f, g)
        } else if (cfg.shadow) {
            timePaint.setShadowLayer(wf * 0.012f, 0f, wf * 0.004f, 0x88000000.toInt())
            datePaint.setShadowLayer(wf * 0.008f, 0f, wf * 0.003f, 0x88000000.toInt())
        } else {
            timePaint.clearShadowLayer()
            datePaint.clearShadowLayer()
        }

        var timeStr = ""
        if (cfg.showTime) {
            val pattern = (if (cfg.use24h) "HH:mm" else "h:mm") + (if (cfg.showSeconds) ":ss" else "") +
                (if (!cfg.use24h && cfg.showAmPm) " a" else "")
            timeStr = SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        }
        val dateStr = if (cfg.showDate)
            SimpleDateFormat(DATE_PATTERNS[cfg.dateFormat.coerceIn(0, DATE_PATTERNS.size - 1)], Locale.getDefault()).format(date)
        else ""

        val y = hf * cfg.posY / 100f
        val timeH = if (cfg.showTime) timePaint.textSize else 0f
        val gap = datePaint.textSize * 0.9f
        if (cfg.dateAbove) {
            if (dateStr.isNotEmpty()) c.drawText(dateStr, x, y, datePaint)
            if (timeStr.isNotEmpty()) c.drawText(timeStr, x, y + (if (dateStr.isNotEmpty()) gap else 0f) + timeH * 0.85f, timePaint)
        } else {
            if (timeStr.isNotEmpty()) c.drawText(timeStr, x, y + timeH * 0.85f, timePaint)
            if (dateStr.isNotEmpty()) c.drawText(dateStr, x, y + timeH * 0.85f + gap + datePaint.textSize * 0.3f, datePaint)
        }

        // ----- robot, drone & effects on top -----
        finish?.invoke()
    }

    /** Milliseconds until the next redraw is needed. */
    fun nextDelay(cfg: LiveConfig, now: Long): Long = when {
        cfg.animated -> 33L
        cfg.showSeconds -> 1000L - now % 1000L
        else -> 60_000L - now % 60_000L
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun lighten(c: Int) = Color.rgb(
        min(255, Color.red(c) + 60), min(255, Color.green(c) + 60), min(255, Color.blue(c) + 60)
    )
    private fun darken(c: Int) = Color.rgb(Color.red(c) / 3, Color.green(c) / 3, Color.blue(c) / 3)

    @Suppress("unused")
    private val TAU = (2 * PI).toFloat()
}
