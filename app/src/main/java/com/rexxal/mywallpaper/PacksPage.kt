package com.rexxal.mywallpaper

import android.graphics.Bitmap
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/** Tab: built-in wallpaper pack. Tap one to open it in Edit; 🎲 makes a fresh pack. */
class PacksPage(private val a: MainActivity) {

    private val c = a
    private fun dp(v: Int) = Ui.dp(c, v)
    private var seed = System.currentTimeMillis() % 100000
    private var palette = 0
    private val thumbs = mutableListOf<ImageView>()
    private var generation = 0

    val view: LinearLayout = build()

    private fun build(): LinearLayout {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(Ui.text(c, "Tap a wallpaper to edit & set it", 13f, Ui.MUTED), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        top.addView(Ui.pill(c, "🎲  New pack") { seed += 1; renderThumbs() })
        root.addView(top)

        root.addView(Ui.choice(c, Generator.PALETTE_NAMES, 0) {
            palette = it
            renderThumbs()
        }.view, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(6) })

        val grid = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(12))
        }
        val perRow = 3
        val cellH = dp(200)
        var row: LinearLayout? = null
        Generator.STYLES.forEachIndexed { i, name ->
            if (i % perRow == 0) {
                row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(row, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(10) })
            }
            val cell = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            val img = ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Ui.rounded(c, Ui.SURFACE2, 14)
                clipToOutline = true
                setOnClickListener { open(i) }
            }
            thumbs += img
            cell.addView(img, LinearLayout.LayoutParams(Ui.MATCH, cellH))
            cell.addView(Ui.text(c, name, 12f, Ui.TEXT).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
            row?.addView(cell, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            })
        }
        // keep the last row's cells the same width
        val rem = Generator.STYLES.size % perRow
        if (rem != 0) repeat(perRow - rem) {
            row?.addView(LinearLayout(c), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(4); rightMargin = dp(4) })
        }
        root.addView(ScrollView(c).apply { addView(grid) }, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        renderThumbs()
        return root
    }

    private fun renderThumbs() {
        val gen = ++generation
        val s = seed
        val pal = palette
        val tw = a.screenW / 5
        val th = a.screenH / 5
        Thread {
            for (i in Generator.STYLES.indices) {
                if (gen != generation) return@Thread
                val bmp: Bitmap = try {
                    Generator.render(i, s, pal, tw, th)
                } catch (e: Throwable) {
                    continue
                }
                a.runOnUiThread { if (gen == generation) thumbs[i].setImageBitmap(bmp) }
            }
        }.start()
    }

    private fun open(i: Int) {
        a.toast("Opening ${Generator.STYLES[i]}…")
        val s = seed
        val pal = palette
        Thread {
            try {
                val bmp = Generator.render(i, s, pal, a.screenW, a.screenH)
                a.runOnUiThread { a.openInEditor(bmp) }
            } catch (e: Throwable) {
                a.runOnUiThread { a.toast("Failed: ${e.message}") }
            }
        }.start()
    }
}
