package com.rexxal.mywallpaper

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.display.DisplayManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Shared "paper" theme: colors (light or dark), fonts, icons and the building blocks every screen uses. */
object Ui {
    // ---- theme tokens (set by init) ----
    var DARK = false
    var BG = 0xFFFBFBF8.toInt()        // paper
    var SURFACE = 0xFFFFFFFF.toInt()   // sheet / card
    var SURFACE2 = 0xFFF1F1EC.toInt()  // soft fill (pills, steppers)
    var ACCENT = 0xFF141414.toInt()    // ink = selected
    var ON_ACCENT = 0xFFFBFBF8.toInt() // text on ink
    var ACCENT_SOFT = 0x55141414
    var TEXT = 0xFF141414.toInt()
    var MUTED = 0xFF5C5C58.toInt()
    var FAINT = 0xFF9A9A94.toInt()
    var RULE = 0xFFE2E1DB.toInt()
    var TRACK = 0xFFE2E1DB.toInt()
    var ACCENT_TEXT = 0xFF141414.toInt()
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    val PALETTE = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF111318.toInt(), 0xFFFF5A5F.toInt(), 0xFFFF9F1C.toInt(),
        0xFFFFD166.toInt(), 0xFF06D6A0.toInt(), 0xFF4CC9F0.toInt(), 0xFF4361EE.toInt(),
        0xFF7C5CFF.toInt(), 0xFFF72585.toInt(), 0xFFA0A4B8.toInt(), 0xFF1B1F3B.toInt()
    )

    // ---- fonts ----
    var sans: Typeface = Typeface.DEFAULT
    var sansMed: Typeface = Typeface.DEFAULT
    var sansBold: Typeface = Typeface.DEFAULT_BOLD
    var mono: Typeface = Typeface.MONOSPACE
    var fa: Typeface? = null

    private fun font(c: Context, name: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(c.assets, "fonts/$name") } catch (e: Exception) { fallback }

    /** Call once per activity start, before building screens. */
    fun init(c: Context) {
        DARK = prefs(c).getBoolean("ui_dark", false)
        if (DARK) {
            BG = 0xFF121211.toInt(); SURFACE = 0xFF1B1B1A.toInt(); SURFACE2 = 0xFF262625.toInt()
            ACCENT = 0xFFF3F2EC.toInt(); ON_ACCENT = 0xFF121211.toInt(); ACCENT_SOFT = 0x55F3F2EC
            TEXT = 0xFFF3F2EC.toInt(); MUTED = 0xFFA8A7A0.toInt(); FAINT = 0xFF6F6E69.toInt(); RULE = 0xFF2E2E2C.toInt(); TRACK = 0xFF3A3A38.toInt(); ACCENT_TEXT = TEXT
        } else {
            BG = 0xFFFBFBF8.toInt(); SURFACE = 0xFFFFFFFF.toInt(); SURFACE2 = 0xFFF1F1EC.toInt()
            ACCENT = 0xFF141414.toInt(); ON_ACCENT = 0xFFFBFBF8.toInt(); ACCENT_SOFT = 0x55141414
            TEXT = 0xFF141414.toInt(); MUTED = 0xFF5C5C58.toInt(); FAINT = 0xFF9A9A94.toInt(); RULE = 0xFFE2E1DB.toInt(); TRACK = 0xFFD8D7D0.toInt(); ACCENT_TEXT = TEXT
        }
        sans = font(c, "IBMPlexSans-Regular.ttf", Typeface.DEFAULT)
        sansMed = font(c, "IBMPlexSans-Medium.ttf", sans)
        sansBold = font(c, "IBMPlexSans-SemiBold.ttf", Typeface.DEFAULT_BOLD)
        mono = font(c, "IBMPlexMono-Regular.ttf", Typeface.MONOSPACE)
        fa = try { Typeface.createFromAsset(c.assets, "fonts/fa-solid-900.ttf") } catch (e: Exception) { null }
    }

    // ---- icons: emoji in labels become Font Awesome glyphs ----
    private val ICONS = mapOf(
        "📷" to '', "📸" to '', "✨" to '', "💾" to '', "↺" to '', "🎨" to '', "🔄" to '',
        "🤖" to '', "🧩" to '', "🕵" to '', "⚽" to '', "🧃" to '', "🎉" to '', "💬" to '',
        "🔓" to '', "😴" to '', "🖼" to '', "🕒" to '', "📅" to '', "🔤" to '', "＋" to '+',
        "⏻" to '', "🔒" to '', "🏠" to '', "▶" to '', "⤢" to '', "⤡" to '', "🧱" to '',
        "🔕" to '', "🗑" to '', "🚨" to '', "🙂" to '', "🤨" to '', "🌑" to '', "📂" to '',
        "✕" to '', "✓" to '', "📁" to '', "⚡" to '', "📌" to '', "📍" to '', "🔍" to ''
    )

    private class IconSpan(val tf: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = tf }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = tf }
    }

    /** Replaces UI emoji with Font Awesome icons (falls back to plain text if the icon font is missing). */
    fun fx(s: CharSequence): CharSequence {
        val f = fa ?: return s
        val str = s.toString().replace("️", "")
        val sb = SpannableStringBuilder()
        var i = 0
        while (i < str.length) {
            var hit: String? = null
            for (k in ICONS.keys) if (str.startsWith(k, i)) { hit = k; break }
            if (hit != null) {
                val start = sb.length; sb.append(ICONS.getValue(hit)); sb.setSpan(IconSpan(f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += hit.length
            } else { sb.append(str[i]); i++ }
        }
        return sb
    }

    /** A single Font Awesome glyph. */
    fun icon(c: Context, glyph: Char, size: Float = 16f, color: Int = TEXT) = TextView(c).apply {
        text = glyph.toString(); textSize = size; setTextColor(color); typeface = fa ?: Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        if (fa == null) text = "•"
    }

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("mw", Context.MODE_PRIVATE)

    /** Real full-screen size in portrait (works from any context). */
    fun screenSize(c: Context): Pair<Int, Int> {
        val d = c.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(dm)
        return Pair(minOf(dm.widthPixels, dm.heightPixels), maxOf(dm.widthPixels, dm.heightPixels))
    }

    fun openBatterySettings(a: android.app.Activity) {
        try { a.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:" + a.packageName))) } catch (e: Exception) { }
        toastSafe(a, "Battery → No restrictions / Allow background activity")
    }

    /** Opens the phone maker's Autostart screen (Xiaomi, Realme/OPPO, Vivo…) or app settings. */
    fun openAutostart(a: android.app.Activity) {
        val comps = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        for ((pkg, cls) in comps) {
            try { a.startActivity(android.content.Intent().setComponent(android.content.ComponentName(pkg, cls))); toastSafe(a, "Turn on MyWallpaper"); return } catch (e: Exception) { }
        }
        openBatterySettings(a)
    }

    /** "Lock screen not working?" help with one-tap fixes. */
    fun lockHelpCard(a: android.app.Activity): LinearLayout {
        val k = card(a)
        k.addView(text(a, "Lock screen not working sometimes?", 15f, TEXT, true))
        k.addView(text(a, "Xiaomi/Redmi (MIUI) and Realme save battery by pausing wallpapers, and some show their own picture on the lock screen. Fix it once:", 13f, MUTED).apply { setPadding(0, dp(a, 4), 0, 0) })
        k.addView(text(a, "1. Battery → No restrictions\n2. Autostart → on\n3. When applying a live wallpaper, choose \"Home & lock screen\"\n4. MIUI: turn off Lock screen \"Glance\" / wallpaper carousel", 13f, TEXT).apply { setPadding(0, dp(a, 8), 0, 0) })
        k.addView(button(a, "Battery settings", false) { openBatterySettings(a) }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(a, 10) })
        k.addView(button(a, "Autostart settings", false) { openAutostart(a) }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(a, 8) })
        return k
    }

    fun toastSafe(c: Context, msg: String) = android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, color: Int, radius: Int = 999) = GradientDrawable().apply {
        cornerRadius = dp(c, radius).toFloat()
        setColor(color)
    }

    /** Rounded fill with a thin outline. */
    fun outlined(c: Context, fill: Int, stroke: Int, radius: Int = 999, width: Float = 1.5f) = GradientDrawable().apply {
        cornerRadius = dp(c, radius).toFloat(); setColor(fill); setStroke((width * c.resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
    }

    /** Darkens a little when pressed. */
    fun pressable(c: Context, normal: GradientDrawable, pressedFill: Int, radius: Int): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply { cornerRadius = dp(c, radius).toFloat(); setColor(pressedFill) })
        addState(intArrayOf(), normal)
    }

    fun text(c: Context, s: String, size: Float = 14f, color: Int = TEXT, bold: Boolean = false) =
        TextView(c).apply {
            text = fx(s)
            textSize = size
            setTextColor(color)
            typeface = if (bold) sansBold else sans
            setLineSpacing(0f, 1.15f)
        }

    /** Small uppercase mono label (kicker / section). */
    fun kicker(c: Context, s: String, color: Int = MUTED) = TextView(c).apply {
        text = s.uppercase(); textSize = 11f; setTextColor(color); typeface = mono; letterSpacing = 0.1f
    }

    /** Section title. Inside a tabbed settings area it becomes one of the category chips. */
    fun section(c: Context, s: String) = kicker(c, s).apply {
        setPadding(dp(c, 4), dp(c, 20), 0, dp(c, 8))
        tag = "section"
        contentDescription = s
    }

    /** Screen header: small kicker, big title, subtitle, a hairline rule; optional view on the right. */
    fun header(c: Context, kick: String, title: String, sub: String, right: View? = null): LinearLayout {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 2), dp(c, 6), dp(c, 2), 0) }
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val txt = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        txt.addView(kicker(c, kick))
        txt.addView(TextView(c).apply { text = title; textSize = 28f; setTextColor(TEXT); typeface = sansBold; letterSpacing = -0.02f; setPadding(0, dp(c, 2), 0, 0) })
        txt.addView(text(c, sub, 13f, MUTED).apply { setPadding(0, dp(c, 2), 0, 0) })
        row.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (right != null) row.addView(right)
        box.addView(row)
        box.addView(View(c).apply { setBackgroundColor(TEXT) }, LinearLayout.LayoutParams(MATCH, dp(c, 1).coerceAtLeast(2)).apply { topMargin = dp(c, 12); bottomMargin = dp(c, 10) })
        return box
    }

    /** Round outlined icon button. */
    fun iconButton(c: Context, glyph: Char, desc: String, onClick: () -> Unit) = icon(c, glyph, 15f, TEXT).apply {
        contentDescription = desc
        background = pressable(c, outlined(c, SURFACE, TEXT), SURFACE2, 999)
        layoutParams = LinearLayout.LayoutParams(dp(c, 42), dp(c, 42))
        setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
    }

    /**
     * Splits a long settings column into categories: one chip per section title, and only the chosen
     * category is shown. [start] is the category to open first; [onTab] reports changes.
     * Switching categories keeps your place on the page.
     */
    fun tabify(c: Context, col: LinearLayout, start: Int = 0, onTab: ((Int) -> Unit)? = null): LinearLayout {
        val kids = (0 until col.childCount).map { col.getChildAt(it) }
        col.removeAllViews()
        val titles = mutableListOf<String>()
        val pages = mutableListOf<LinearLayout>()
        fun newPage(t: String) { titles += t; pages += LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(c, 4), 0, dp(c, 16)) } }
        for (v in kids) {
            if (v.tag == "section") { newPage(v.contentDescription?.toString() ?: "More"); continue }
            if (pages.isEmpty()) newPage("General")
            pages.last().addView(v)
        }
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        if (pages.isEmpty()) return root
        val host = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(c).apply { addView(host); isVerticalScrollBarEnabled = false }
        val first = start.coerceIn(0, pages.size - 1)
        fun show(i: Int) { host.removeAllViews(); host.addView(pages[i]) }
        if (pages.size > 1) root.addView(choice(c, titles, first) { show(it); onTab?.invoke(it) }.view)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        show(first)
        return root
    }

    /** Preview area height: small or large share of the screen. */
    fun previewHeight(c: Context, large: Boolean): Int {
        val h = c.resources.displayMetrics.heightPixels
        return (h * if (large) 0.52f else 0.34f).toInt()
    }

    /** Small button that switches a preview between small and large. */
    fun previewToggle(c: Context, target: View): TextView {
        var large = false
        return pill(c, "⤢  Bigger preview") {}.apply {
            setOnClickListener {
                large = !large
                target.layoutParams = target.layoutParams.apply { height = previewHeight(c, large) }
                target.requestLayout()
                text = fx(if (large) "⤡  Smaller preview" else "⤢  Bigger preview")
            }
        }
    }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = outlined(c, SURFACE, RULE, 18, 1f)
        setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 12))
    }

    fun button(c: Context, label: String, primary: Boolean, onClick: () -> Unit) = TextView(c).apply {
        text = fx(label)
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = sansBold
        setTextColor(if (primary) ON_ACCENT else TEXT)
        background = pressable(c, outlined(c, if (primary) ACCENT else SURFACE, ACCENT, 12), if (primary) MUTED else SURFACE2, 12)
        setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14))
        setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); onClick() }
    }

    fun pill(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = fx(label)
        textSize = 13f
        typeface = sansMed
        setTextColor(TEXT)
        background = pressable(c, outlined(c, SURFACE, TEXT, 999, 1f), SURFACE2, 999)
        setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9))
        setOnClickListener { onClick() }
    }

    fun slider(
        c: Context, parent: LinearLayout, label: String, maxValue: Int, value: Int,
        show: (Int) -> String, onChange: (Int) -> Unit
    ): SeekBar {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(c, 8), 0, dp(c, 6)) }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val valueTv = TextView(c).apply { text = show(value); textSize = 12f; typeface = mono; setTextColor(TEXT); background = outlined(c, SURFACE, TEXT, 999, 1f); setPadding(dp(c, 9), dp(c, 2), dp(c, 9), dp(c, 2)) }
        head.addView(text(c, label, 14f, TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(valueTv, LinearLayout.LayoutParams(WRAP, WRAP))
        val seek = SeekBar(c)
        seek.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        seek.max = maxValue
        seek.progress = value
        seek.thumb = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(SURFACE); setStroke(dp(c, 2), ACCENT); setSize(dp(c, 24), dp(c, 24)) }
        seek.progressTintList = ColorStateList.valueOf(ACCENT)
        seek.progressBackgroundTintList = ColorStateList.valueOf(TRACK)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                valueTv.text = show(p)
                onChange(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        fun step(glyph: Char, d: Int) = icon(c, glyph, 13f, TEXT).apply {
            background = pressable(c, outlined(c, SURFACE, RULE, 12, 1f), SURFACE2, 12)
            setOnClickListener { seek.progress = (seek.progress + d).coerceIn(0, seek.max) }
            setOnLongClickListener { seek.progress = (seek.progress + d * maxOf(1, seek.max / 10)).coerceIn(0, seek.max); true }
        }
        val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(c, 4), 0, 0) }
        line.addView(step('', -1), LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)))
        line.addView(seek, LinearLayout.LayoutParams(0, WRAP, 1f))
        line.addView(step('+', 1), LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)))
        box.addView(head)
        box.addView(line)
        parent.addView(box)
        return seek
    }

    fun toggle(c: Context, parent: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = Switch(c)
        sw.text = fx(label)
        sw.textSize = 14f
        sw.typeface = sansMed
        sw.setTextColor(TEXT)
        sw.isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(ON_ACCENT, ACCENT))
        sw.trackTintList = ColorStateList(states, intArrayOf(ACCENT, TRACK))
        sw.trackTintMode = android.graphics.PorterDuff.Mode.SRC_IN
        sw.setPadding(0, dp(c, 10), 0, dp(c, 10))
        sw.minHeight = dp(c, 52)
        sw.setOnCheckedChangeListener { v, b -> v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); onChange(b) }
        parent.addView(sw, LinearLayout.LayoutParams(MATCH, WRAP))
        return sw
    }

    /** A scrollable row of chips where exactly one is selected. */
    class Choice(val view: View, private val chips: List<TextView>, private val c: Context) {
        fun select(i: Int) {
            chips.forEachIndexed { k, tv ->
                val sel = k == i
                tv.background = outlined(c, if (sel) ACCENT else SURFACE, ACCENT, 999, 1f)
                tv.setTextColor(if (sel) ON_ACCENT else TEXT)
            }
        }
    }

    fun choice(c: Context, labels: List<String>, selected: Int, onPick: (Int) -> Unit): Choice {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableListOf<TextView>()
        lateinit var ch: Choice
        labels.forEachIndexed { i, l ->
            val tv = TextView(c).apply {
                text = fx(l)
                textSize = 13f
                typeface = sansMed
                setPadding(dp(c, 15), dp(c, 9), dp(c, 15), dp(c, 9))
                setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); ch.select(i); onPick(i) }
            }
            chips += tv
            row.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(c, 8) })
        }
        val scroll = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            setPadding(0, dp(c, 6), 0, dp(c, 6))
        }
        ch = Choice(scroll, chips, c)
        ch.select(selected)
        return ch
    }

    /** Row of color circles. */
    fun colors(c: Context, selected: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val dots = mutableListOf<Pair<View, Int>>()
        var sel = selected
        fun paint() = dots.forEach { (v, col) ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(col)
                setStroke(dp(c, if (col == sel) 3 else 1), if (col == sel) ACCENT else RULE)
            }
        }
        PALETTE.forEach { col ->
            val v = View(c)
            v.setOnClickListener {
                sel = col
                paint()
                onPick(col)
            }
            dots += v to col
            row.addView(v, LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)).apply { rightMargin = dp(c, 12) })
        }
        paint()
        return HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            setPadding(0, dp(c, 4), 0, dp(c, 6))
        }
    }

    fun label(c: Context, s: String) = text(c, s, 13f, MUTED).apply { setPadding(0, dp(c, 10), 0, 0) }

    // ---------------- list rows (Home, Features, Profile) ----------------

    /** A group of rows inside one outlined sheet. */
    fun list(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = outlined(c, SURFACE, RULE, 18, 1f)
        clipToOutline = true
    }

    /** Row: icon tile, title + helper text, and something on the right (value, chevron…). */
    fun row(c: Context, glyph: Char, title: String, sub: String?, right: View? = null, solid: Boolean = false, onClick: (() -> Unit)? = null): LinearLayout {
        val r = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12)); minimumHeight = dp(c, 62)
            if (onClick != null) { background = pressable(c, GradientDrawable().apply { setColor(SURFACE) }, SURFACE2, 0); setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); onClick() } }
        }
        r.addView(icon(c, glyph, 14f, if (solid) ON_ACCENT else TEXT).apply { background = outlined(c, if (solid) ACCENT else SURFACE, ACCENT, 10, 1f) }, LinearLayout.LayoutParams(dp(c, 36), dp(c, 36)))
        val t = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 12), 0, dp(c, 8), 0) }
        t.addView(text(c, title, 15f, TEXT, true))
        if (!sub.isNullOrEmpty()) t.addView(text(c, sub, 12f, MUTED).apply { setPadding(0, dp(c, 2), 0, 0) })
        r.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (right != null) r.addView(right) else if (onClick != null) r.addView(icon(c, '', 11f, FAINT))
        return r
    }

    /** Adds rows to a list with hairline separators. */
    fun addRows(c: Context, list: LinearLayout, rows: List<View>) {
        rows.forEachIndexed { i, v ->
            if (i > 0) list.addView(View(c).apply { setBackgroundColor(RULE) }, LinearLayout.LayoutParams(MATCH, 1))
            list.addView(v)
        }
    }

    fun value(c: Context, s: String) = TextView(c).apply { text = s; textSize = 12f; typeface = mono; setTextColor(MUTED) }
}
