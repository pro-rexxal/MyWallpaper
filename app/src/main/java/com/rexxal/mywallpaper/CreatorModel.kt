package com.rexxal.mywallpaper

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One text layer: the clock, the date, or your own text. */
class TextEl(var kind: Int = KIND_TEXT) {
    companion object {
        const val KIND_TIME = 0
        const val KIND_DATE = 1
        const val KIND_TEXT = 2
    }

    var label = "Text"
    var text = "Hello"
    // time
    var use24 = false
    var seconds = false
    var ampm = false
    var leadZero = false
    var stacked = false
    // date
    var datePattern = "EEEE, d MMMM"
    // font & layout
    var font = 0
    var bold = false
    var italic = false
    var customFont: String? = null
    var size = 10f            // % of screen width
    var spacing = 0           // letter spacing, 1/100 em
    var align = 1             // 0 left, 1 center, 2 right
    var x = 0.5f
    var y = 0.2f
    var opacity = 100
    // color
    var color = 0xFFFFFFFF.toInt()
    var gradient = false
    var color2 = 0xFF7C5CFF.toInt()
    // effects
    var shadow = true
    var shadowColor = 0xAA000000.toInt()
    var shadowBlur = 10
    var shadowDy = 4
    var depth = 0             // 3D extrude steps
    var depthAngle = 45
    var depthColor = 0xFF2A2D3E.toInt()
    var outline = 0
    var outlineColor = 0xFF000000.toInt()
    var glow = 0
    var glowColor = 0xFF7C5CFF.toInt()
    // animation
    var idle = 0
    var inAnim = 1
    var outAnim = 1
    var duration = 700
    var delay = 0
    // where it shows
    var showLock = true
    var showHome = true

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind); put("label", label); put("text", text)
        put("use24", use24); put("seconds", seconds); put("ampm", ampm); put("leadZero", leadZero); put("stacked", stacked)
        put("datePattern", datePattern)
        put("font", font); put("bold", bold); put("italic", italic); put("customFont", customFont ?: "")
        put("size", size.toDouble()); put("spacing", spacing); put("align", align)
        put("x", x.toDouble()); put("y", y.toDouble()); put("opacity", opacity)
        put("color", color); put("gradient", gradient); put("color2", color2)
        put("shadow", shadow); put("shadowColor", shadowColor); put("shadowBlur", shadowBlur); put("shadowDy", shadowDy)
        put("depth", depth); put("depthAngle", depthAngle); put("depthColor", depthColor)
        put("outline", outline); put("outlineColor", outlineColor); put("glow", glow); put("glowColor", glowColor)
        put("idle", idle); put("inAnim", inAnim); put("outAnim", outAnim); put("duration", duration); put("delay", delay)
        put("showLock", showLock); put("showHome", showHome)
    }

    fun copyFrom(o: JSONObject): TextEl {
        kind = o.optInt("kind", kind); label = o.optString("label", label); text = o.optString("text", text)
        use24 = o.optBoolean("use24", use24); seconds = o.optBoolean("seconds", seconds); ampm = o.optBoolean("ampm", ampm)
        leadZero = o.optBoolean("leadZero", leadZero); stacked = o.optBoolean("stacked", stacked)
        datePattern = o.optString("datePattern", datePattern)
        font = o.optInt("font", font); bold = o.optBoolean("bold", bold); italic = o.optBoolean("italic", italic)
        customFont = o.optString("customFont", "").ifEmpty { null }
        size = o.optDouble("size", size.toDouble()).toFloat(); spacing = o.optInt("spacing", spacing); align = o.optInt("align", align)
        x = o.optDouble("x", x.toDouble()).toFloat(); y = o.optDouble("y", y.toDouble()).toFloat(); opacity = o.optInt("opacity", opacity)
        color = o.optInt("color", color); gradient = o.optBoolean("gradient", gradient); color2 = o.optInt("color2", color2)
        shadow = o.optBoolean("shadow", shadow); shadowColor = o.optInt("shadowColor", shadowColor)
        shadowBlur = o.optInt("shadowBlur", shadowBlur); shadowDy = o.optInt("shadowDy", shadowDy)
        depth = o.optInt("depth", depth); depthAngle = o.optInt("depthAngle", depthAngle); depthColor = o.optInt("depthColor", depthColor)
        outline = o.optInt("outline", outline); outlineColor = o.optInt("outlineColor", outlineColor)
        glow = o.optInt("glow", glow); glowColor = o.optInt("glowColor", glowColor)
        idle = o.optInt("idle", idle); inAnim = o.optInt("inAnim", inAnim); outAnim = o.optInt("outAnim", outAnim)
        duration = o.optInt("duration", duration); delay = o.optInt("delay", delay)
        showLock = o.optBoolean("showLock", showLock); showHome = o.optBoolean("showHome", showHome)
        return this
    }
}

class CreatorBg {
    var type = 0              // 0 photo, 1 gradient, 2 solid
    var c1 = 0xFF1B1F3B.toInt()
    var c2 = 0xFF7C5CFF.toInt()
    var angle = 90
    var dimLock = 10
    var dimHome = 35
    var zoomHome = 8
    var blurHome = true

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type); put("c1", c1); put("c2", c2); put("angle", angle)
        put("dimLock", dimLock); put("dimHome", dimHome); put("zoomHome", zoomHome); put("blurHome", blurHome)
    }

    fun copyFrom(o: JSONObject) {
        type = o.optInt("type", type); c1 = o.optInt("c1", c1); c2 = o.optInt("c2", c2); angle = o.optInt("angle", angle)
        dimLock = o.optInt("dimLock", dimLock); dimHome = o.optInt("dimHome", dimHome)
        zoomHome = o.optInt("zoomHome", zoomHome); blurHome = o.optBoolean("blurHome", blurHome)
    }
}

class Design {
    val bg = CreatorBg()
    val items = mutableListOf<TextEl>()

    fun save(p: SharedPreferences) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        val o = JSONObject().put("bg", bg.toJson()).put("items", arr)
        p.edit().putString(KEY, o.toString()).apply()
    }

    companion object {
        const val KEY = "creator_design"

        fun photoFile(c: Context) = File(c.filesDir, "creator_bg.jpg")
        fun fontsDir(c: Context) = File(c.filesDir, "fonts").apply { mkdirs() }

        fun load(p: SharedPreferences): Design {
            val d = Design()
            val s = p.getString(KEY, null)
            if (s == null) {
                d.items += defaults()
                return d
            }
            try {
                val o = JSONObject(s)
                o.optJSONObject("bg")?.let { d.bg.copyFrom(it) }
                val arr = o.optJSONArray("items")
                if (arr != null) for (i in 0 until arr.length()) d.items += TextEl().copyFrom(arr.getJSONObject(i))
            } catch (e: Exception) {
                d.items.clear()
                d.items += defaults()
            }
            return d
        }

        fun defaults(): List<TextEl> = listOf(
            TextEl(TextEl.KIND_TIME).apply {
                label = "Time"; size = 24f; y = 0.2f; font = 2; inAnim = 7; outAnim = 6; glow = 30
            },
            TextEl(TextEl.KIND_DATE).apply {
                label = "Date"; size = 5f; y = 0.27f; inAnim = 2; outAnim = 3; delay = 150; spacing = 10
            },
            TextEl(TextEl.KIND_TEXT).apply {
                label = "Text"; text = "Hello"; size = 9f; y = 0.78f; font = 4; inAnim = 12; outAnim = 1
                showLock = true; showHome = false; glow = 40; delay = 300
            }
        )
    }
}
