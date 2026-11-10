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

    fun section(c: Context, s: String) = text(c, s.uppercase(), 11f, MUTED, true).apply {
        letterSpacing = 0.08f
        setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 8))
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
        setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
        setOnClickListener { onClick() }
    }

    fun slider(
        c: Context, parent: LinearLayout, label: String, maxValue: Int, value: Int,
        show: (Int) -> String, onChange: (Int) -> Unit
    ): SeekBar {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(c, 3), 0, dp(c, 3))
        }
        val valueTv = text(c, show(value), 12f, MUTED).apply { gravity = Gravity.END }
        val seek = SeekBar(c)
        seek.max = maxValue
        seek.progress = value
        seek.progressTintList = ColorStateList.valueOf(ACCENT)
        seek.thumbTintList = ColorStateList.valueOf(ACCENT)
        seek.progressBackgroundTintList = ColorStateList.valueOf(TRACK)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                valueTv.text = show(p)
                onChange(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        row.addView(text(c, label, 13f), LinearLayout.LayoutParams(dp(c, 92), WRAP))
        row.addView(seek, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(valueTv, LinearLayout.LayoutParams(dp(c, 42), WRAP))
        parent.addView(row)
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
        sw.setPadding(0, dp(c, 8), 0, dp(c, 8))
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
                textSize = 13f
                setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7))
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
            row.addView(v, LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)).apply { rightMargin = dp(c, 10) })
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
