package com.rexxal.mywallpaper

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
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

/** Shared colors, sizes and little UI building blocks used by every tab. */
object Ui {
    val BG = 0xFF0E0F13.toInt()
    val SURFACE = 0xFF181A21.toInt()
    val SURFACE2 = 0xFF242731.toInt()
    val ACCENT = 0xFF7C5CFF.toInt()
    val ACCENT_SOFT = 0x887C5CFF.toInt()
    val TEXT = 0xFFEDEDF2.toInt()
    val MUTED = 0xFF8A8D98.toInt()
    val TRACK = 0xFF3A3D47.toInt()
    val ACCENT_TEXT = 0xFFB9A6FF.toInt()
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    val PALETTE = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF111318.toInt(), 0xFFFF5A5F.toInt(), 0xFFFF9F1C.toInt(),
        0xFFFFD166.toInt(), 0xFF06D6A0.toInt(), 0xFF4CC9F0.toInt(), 0xFF4361EE.toInt(),
        0xFF7C5CFF.toInt(), 0xFFF72585.toInt(), 0xFFA0A4B8.toInt(), 0xFF1B1F3B.toInt()
    )

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("mw", Context.MODE_PRIVATE)

    /** Real full-screen size in portrait (works from any context). */
    fun screenSize(c: Context): Pair<Int, Int> {
        val d = c.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(dm)
        return Pair(minOf(dm.widthPixels, dm.heightPixels), maxOf(dm.widthPixels, dm.heightPixels))
    }

    fun toastSafe(c: Context, msg: String) = android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, color: Int, radius: Int = 999) = GradientDrawable().apply {
        cornerRadius = dp(c, radius).toFloat()
        setColor(color)
    }

    fun text(c: Context, s: String, size: Float = 14f, color: Int = TEXT, bold: Boolean = false) =
        TextView(c).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    /** Section title. Inside a tabbed settings area it becomes one of the category chips. */
    fun section(c: Context, s: String) = text(c, s.uppercase(), 11f, MUTED, true).apply {
        letterSpacing = 0.08f
        setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 10))
        tag = "section"
        contentDescription = s
    }

    /**
     * Splits a long settings column into categories: one chip per section title, and only the chosen
     * category is shown. [start] is the category to open first; [onTab] reports changes.
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
        val scroll = ScrollView(c).apply { addView(host) }
        val first = start.coerceIn(0, pages.size - 1)
        fun show(i: Int) { host.removeAllViews(); host.addView(pages[i]); scroll.scrollTo(0, 0) }
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
                text = if (large) "⤡  Smaller preview" else "⤢  Bigger preview"
            }
        }
    }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c, SURFACE, 18)
        setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 12))
    }

    fun button(c: Context, label: String, primary: Boolean, onClick: () -> Unit) = TextView(c).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else TEXT)
        background = rounded(c, if (primary) ACCENT else SURFACE2, 14)
        setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14))
        setOnClickListener { onClick() }
    }

    fun pill(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = label
        textSize = 13f
        setTextColor(TEXT)
        background = rounded(c, SURFACE2)
        setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10))
        setOnClickListener { onClick() }
    }

    fun slider(
        c: Context, parent: LinearLayout, label: String, maxValue: Int, value: Int,
        show: (Int) -> String, onChange: (Int) -> Unit
    ): SeekBar {
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(c, 6), 0, dp(c, 6)) }
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val valueTv = text(c, show(value), 14f, ACCENT_TEXT, true).apply { gravity = Gravity.END }
        head.addView(text(c, label, 14f), LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(valueTv, LinearLayout.LayoutParams(WRAP, WRAP))
        val seek = SeekBar(c)
        seek.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12))
        seek.max = maxValue
        seek.progress = value
        seek.thumb = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT); setStroke(dp(c, 3), 0xFFFFFFFF.toInt()); setSize(dp(c, 26), dp(c, 26)) }
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
        fun step(label: String, d: Int) = TextView(c).apply {
            text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(TEXT)
            background = rounded(c, SURFACE2, 12)
            setOnClickListener { seek.progress = (seek.progress + d).coerceIn(0, seek.max) }
            setOnLongClickListener { seek.progress = (seek.progress + d * maxOf(1, seek.max / 10)).coerceIn(0, seek.max); true }
        }
        val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(step("−", -1), LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)))
        line.addView(seek, LinearLayout.LayoutParams(0, WRAP, 1f))
        line.addView(step("+", 1), LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)))
        box.addView(head)
        box.addView(line)
        parent.addView(box)
        return seek
    }

    fun toggle(c: Context, parent: LinearLayout, label: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = Switch(c)
        sw.text = label
        sw.textSize = 14f
        sw.setTextColor(TEXT)
        sw.isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(ACCENT, 0xFFB0B3BD.toInt()))
        sw.trackTintList = ColorStateList(states, intArrayOf(ACCENT_SOFT, TRACK))
        sw.setPadding(0, dp(c, 10), 0, dp(c, 10))
        sw.minHeight = dp(c, 48)
        sw.setOnCheckedChangeListener { _, b -> onChange(b) }
        parent.addView(sw, LinearLayout.LayoutParams(MATCH, WRAP))
        return sw
    }

    /** A scrollable row of chips where exactly one is selected. */
    class Choice(val view: View, private val chips: List<TextView>, private val c: Context) {
        fun select(i: Int) {
            chips.forEachIndexed { k, tv ->
                val sel = k == i
                tv.background = rounded(c, if (sel) ACCENT else SURFACE2)
                tv.setTextColor(if (sel) 0xFFFFFFFF.toInt() else TEXT)
            }
        }
    }

    fun choice(c: Context, labels: List<String>, selected: Int, onPick: (Int) -> Unit): Choice {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableListOf<TextView>()
        lateinit var ch: Choice
        labels.forEachIndexed { i, l ->
            val tv = TextView(c).apply {
                text = l
                textSize = 14f
                setPadding(dp(c, 18), dp(c, 11), dp(c, 18), dp(c, 11))
                setOnClickListener { ch.select(i); onPick(i) }
            }
            chips += tv
            row.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(c, 8) })
        }
        val scroll = HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            setPadding(0, dp(c, 4), 0, dp(c, 4))
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
                setStroke(dp(c, if (col == sel) 3 else 1), if (col == sel) 0xFFFFFFFF.toInt() else 0x33FFFFFF)
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
            row.addView(v, LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)).apply { rightMargin = dp(c, 12) })
        }
        paint()
        return HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            setPadding(0, dp(c, 4), 0, dp(c, 6))
        }
    }

    fun label(c: Context, s: String) = text(c, s, 13f, MUTED).apply { setPadding(0, dp(c, 8), 0, 0) }
}
