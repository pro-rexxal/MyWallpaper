package com.rexxal.mywallpaper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.FileOutputStream

/**
 * Full-screen editor for the icon map. Shows a screenshot of each home page (if you add one) at real size,
 * with the launcher grid on top. Tap a spot to mark an icon there.
 */
class IconMapActivity : Activity() {
    private lateinit var map: IconMap
    private lateinit var view: MapView
    private lateinit var pageLabel: TextView
    private lateinit var gridPanel: View
    private var page = 0
    private val bgs = arrayOfNulls<Bitmap>(5)
    private fun dp(v: Int) = Ui.dp(this, v)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // draw edge to edge so the grid lines up with the real screen
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        map = IconMap.load(Ui.prefs(this))
        for (p in 0 until 5) loadBg(p)

        val root = FrameLayout(this)
        view = MapView()
        root.addView(view, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        // grid settings panel (top)
        gridPanel = buildGridPanel()
        gridPanel.visibility = View.GONE
        root.addView(gridPanel, FrameLayout.LayoutParams(Ui.MATCH, (resources.displayMetrics.heightPixels * 0.5f).toInt(), Gravity.TOP))

        // toolbar (bottom)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(this@IconMapActivity, 0xE6181A21.toInt(), 22)
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        bar.addView(Ui.text(this, "Tap where each app icon is. Add a screenshot of the page to see your icons here.", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER })
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        r1.addView(Ui.pill(this, "◀") { setPage(page - 1) })
        pageLabel = Ui.text(this, "", 15f, Ui.TEXT, true).apply { gravity = Gravity.CENTER }
        r1.addView(pageLabel, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        r1.addView(Ui.pill(this, "▶") { setPage(page + 1) })
        bar.addView(r1)
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        fun add(row: LinearLayout, v: View) = row.addView(v, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        add(r2, Ui.button(this, "🖼 Screenshot", false) { pickShot() })
        add(r2, Ui.button(this, "⚙ Grid", false) { gridPanel.visibility = if (gridPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
        add(r2, Ui.button(this, "Clear page", false) { map.cells[page].clear(); save() })
        bar.addView(r2)
        bar.addView(Ui.button(this, "Done", true) { save(); finish() }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        root.addView(bar, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), dp(28)) })

        setContentView(root)
        setPage(0)
    }

    private fun buildGridPanel(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(36), dp(14), dp(14)) }
        col.addView(Ui.text(this, "Line the boxes up with your icons", 15f, Ui.TEXT, true))
        col.addView(Ui.text(this, "Match your launcher's layout (e.g. 4 × 6). Use a screenshot to line it up exactly.", 12f, Ui.MUTED))
        fun s(label: String, min: Int, max: Int, v: Int, show: (Int) -> String = { "$it" }, set: (Int) -> Unit) =
            Ui.slider(this, col, label, max - min, v - min, { show(it + min) }) { set(it + min); save() }
        s("Columns", 3, 6, map.cols) { map.cols = it }
        s("Rows", 1, 8, map.rows) { map.rows = it }
        s("First row", 3, 60, map.top, { "$it%" }) { map.top = it }
        s("Last row", 20, 85, map.bottom, { "$it%" }) { map.bottom = it }
        s("Side margin", 0, 15, map.side, { "$it%" }) { map.side = it }
        s("Icon size", 30, 100, map.size, { "$it%" }) { map.size = it }
        s("Home pages", 1, 5, map.pages) { map.pages = it; setPage(page.coerceAtMost(it - 1)) }
        Ui.toggle(this, col, "Dock row (same on every page)", map.dock) { map.dock = it; save() }
        s("Dock position", 70, 98, map.dockY, { "$it%" }) { map.dockY = it }
        col.addView(Ui.button(this, "Close", true) { gridPanel.visibility = View.GONE }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        return ScrollView(this).apply { addView(col); setBackgroundColor(0xF0101218.toInt()) }
    }

    private fun setPage(p: Int) {
        page = p.coerceIn(0, map.pages - 1)
        pageLabel.text = "Home page ${page + 1} of ${map.pages}"
        view.invalidate()
    }

    private fun save() { map.save(Ui.prefs(this)); view.invalidate() }

    private fun loadBg(p: Int) {
        val f = IconMap.bgFile(this, p)
        bgs[p] = if (f.exists()) try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null } else null
    }

    private fun pickShot() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, 7)
        } catch (e: Exception) { Ui.toastSafe(this, "Photo picker not available") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != 7 || resultCode != RESULT_OK) return
        val p = page
        Thread {
            try {
                val (sw, sh) = Ui.screenSize(this)
                val bmp = ImageLoader.load(this, uri, sw, sh, 6_000_000L)
                val out = Bitmap.createScaledBitmap(bmp, sw, sh, true)   // screenshots are already the screen's shape
                FileOutputStream(IconMap.bgFile(this, p)).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                runOnUiThread { loadBg(p); view.invalidate() }
            } catch (e: Throwable) {
                runOnUiThread { Ui.toastSafe(this, "Couldn't open that picture") }
            }
        }.start()
    }

    inner class MapView : View(this@IconMapActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
        private val r = RectF()

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val d = resources.displayMetrics.density
            val bg = bgs[page]
            if (bg != null) { r.set(0f, 0f, w, h); c.drawBitmap(bg, null, r, p); p.color = 0x55000000; c.drawRect(0f, 0f, w, h, p) }
            else {
                p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF1B1F3B.toInt(), 0xFF0B0C12.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, h, p); p.shader = null
                txt.textSize = 14 * d; txt.color = 0x99FFFFFF.toInt(); c.drawText("No screenshot for this page yet", w / 2, h * 0.08f, txt)
            }
            val (iw, ih) = map.iconSize(w / h)
            fun cell(cx: Float, cy: Float, on: Boolean) {
                r.set((cx - iw / 2) * w, (cy - ih / 2) * h, (cx + iw / 2) * w, (cy + ih / 2) * h)
                if (on) { p.color = 0x997C5CFF.toInt(); c.drawRoundRect(r, iw * w * .26f, iw * w * .26f, p) }
                stroke.color = if (on) 0xFFFFFFFF.toInt() else 0x88FFFFFF.toInt(); stroke.strokeWidth = (if (on) 2.5f else 1.2f) * d
                stroke.pathEffect = if (on) null else android.graphics.DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
                c.drawRoundRect(r, iw * w * .26f, iw * w * .26f, stroke)
                if (on) { txt.textSize = iw * w * .4f; txt.color = Color.WHITE; c.drawText("✓", r.centerX(), r.centerY() + iw * w * .14f, txt) }
            }
            for (row in 0 until map.rows) for (col in 0 until map.cols) { val (cx, cy) = map.cellCenter(col, row); cell(cx, cy, (row * map.cols + col) in map.cells[page]) }
            if (map.dock) {
                p.color = 0x22FFFFFF; val (_, dy) = map.dockCenter(0); r.set(w * .03f, (dy - ih * .9f) * h, w * .97f, (dy + ih * .9f) * h); c.drawRoundRect(r, 20 * d, 20 * d, p)
                for (col in 0 until map.cols) { val (cx, cy) = map.dockCenter(col); cell(cx, cy, col in map.dockCells) }
                txt.textSize = 10 * d; txt.color = 0xAAFFFFFF.toInt(); c.drawText("DOCK · same on every page", w / 2, (dy + ih * .9f) * h + 12 * d, txt)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP) return true
            val nx = e.x / width; val ny = e.y / height
            val (iw, ih) = map.iconSize(width.toFloat() / height)
            var best = -1; var dock = false; var bestD = Float.MAX_VALUE
            for (row in 0 until map.rows) for (col in 0 until map.cols) {
                val (cx, cy) = map.cellCenter(col, row); val dd = (nx - cx) * (nx - cx) + ((ny - cy) * height / width).let { it * it }
                if (dd < bestD) { bestD = dd; best = row * map.cols + col; dock = false }
            }
            if (map.dock) for (col in 0 until map.cols) {
                val (cx, cy) = map.dockCenter(col); val dd = (nx - cx) * (nx - cx) + ((ny - cy) * height / width).let { it * it }
                if (dd < bestD) { bestD = dd; best = col; dock = true }
            }
            val reach = iw * 0.9f
            if (best >= 0 && bestD < reach * reach * 1.6f + (ih * ih)) {
                val set = if (dock) map.dockCells else map.cells[page]
                if (!set.add(best)) set.remove(best)
                save()
            }
            return true
        }
    }
}
