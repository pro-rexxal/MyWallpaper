package com.rexxal.mywallpaper

import android.content.ComponentName
import android.content.Intent
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

/** Creator tab: background + time, date and text layers with effects and lock/home animations. */
class CreatorPage(private val a: MainActivity) {

    private val c = a
    private val p = Ui.prefs(c)
    private fun dp(v: Int) = Ui.dp(c, v)
    private val design = Design.load(p)
    private lateinit var preview: CreatorPreviewView
    private lateinit var layerRow: LinearLayout
    private lateinit var editor: LinearLayout
    private lateinit var editorHost: android.widget.FrameLayout
    private var editorTab = 0
    private var sel = 0          // -1 = background

    val view: LinearLayout = build()

    // ---------------- layout ----------------
    private fun build(): LinearLayout {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        preview = CreatorPreviewView(c).apply {
            aspect = a.screenW.toFloat() / a.screenH
            design = this@CreatorPage.design
            onSelect = { i -> select(i) }
            onMoved = { save() }
        }
        preview.state.go(CreatorState.LOCK, design, System.currentTimeMillis(), instant = true)
        root.addView(preview, LinearLayout.LayoutParams(Ui.MATCH, Ui.previewHeight(c, false)))

        // stage controls
        val stages = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf<Pair<String, () -> Unit>>(
            "⏻ Wake" to { wake() },
            "🔒 Lock" to { stage(CreatorState.LOCK) },
            "🏠 Home" to { stage(CreatorState.HOME) },
            "▶ Unlock demo" to { demo() }
        ).forEach { (l, f) ->
            stages.addView(Ui.pill(c, l, f), LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(6) })
        }
        stages.addView(Ui.previewToggle(c, preview))
        root.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(stages); setPadding(0, dp(6), 0, dp(4)) })

        // layers
        layerRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(layerRow); setPadding(0, dp(2), 0, dp(4)) })

        editor = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        editorHost = android.widget.FrameLayout(c)
        root.addView(editorHost, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        root.addView(Ui.button(c, "Set as Live Wallpaper", true) { setLive() },
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })

        reloadBg()
        select(if (design.items.isEmpty()) -1 else 0)
        return root
    }

    fun refresh() = preview.postInvalidate()

    private fun save() {
        design.save(p)
        preview.invalidate()
    }

    private fun now() = System.currentTimeMillis()

    private fun stage(s: Int) {
        preview.state.go(s, design, now())
        preview.invalidate()
    }

    private fun wake() {
        preview.state.go(CreatorState.OFF, design, now(), instant = true)
        preview.postDelayed({ stage(CreatorState.LOCK) }, 350)
        preview.invalidate()
    }

    private fun demo() {
        wake()
        preview.postDelayed({ stage(CreatorState.HOME) }, 2600)
    }

    private fun select(i: Int) {
        sel = i
        preview.selected = i
        buildLayerRow()
        buildEditor()
        preview.invalidate()
    }

    private fun buildLayerRow() {
        layerRow.removeAllViews()
        fun chip(label: String, on: Boolean, f: () -> Unit) = TextView(c).apply {
            text = label
            textSize = 13f
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = Ui.rounded(c, if (on) Ui.ACCENT else Ui.SURFACE2)
            setTextColor(if (on) 0xFFFFFFFF.toInt() else Ui.TEXT)
            setOnClickListener { f() }
        }
        val lp = { LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(8) } }
        layerRow.addView(chip("🖼 Background", sel == -1) { select(-1) }, lp())
        design.items.forEachIndexed { i, el ->
            val icon = when (el.kind) { TextEl.KIND_TIME -> "🕒"; TextEl.KIND_DATE -> "📅"; else -> "🔤" }
            layerRow.addView(chip("$icon ${el.label}", sel == i) { select(i) }, lp())
        }
        layerRow.addView(chip("＋ Text", false) { add(TextEl.KIND_TEXT) }, lp())
        layerRow.addView(chip("＋ Clock", false) { add(TextEl.KIND_TIME) }, lp())
        layerRow.addView(chip("＋ Date", false) { add(TextEl.KIND_DATE) }, lp())
    }

    private fun add(kind: Int) {
        val el = TextEl(kind).apply {
            label = when (kind) { TextEl.KIND_TIME -> "Clock"; TextEl.KIND_DATE -> "Date"; else -> "Text ${design.items.count { it.kind == TextEl.KIND_TEXT } + 1}" }
            size = when (kind) { TextEl.KIND_TIME -> 20f; TextEl.KIND_DATE -> 5f; else -> 8f }
            y = 0.5f
            text = "Your text"
        }
        design.items += el
        preview.state.go(preview.state.stage, design, now(), instant = true)
        preview.state.replay(design.items.size - 1, design, now())
        save()
        select(design.items.size - 1)
    }

    private fun delete(i: Int) {
        if (i !in design.items.indices) return
        design.items.removeAt(i)
        // rebuild animation slots so they line up with the remaining layers
        preview.state.go(preview.state.stage, design, now(), instant = true)
        save()
        select(if (design.items.isEmpty()) -1 else (i - 1).coerceAtLeast(0))
    }

    // ---------------- editor ----------------
    private fun buildEditor() {
        editor.removeAllViews()
        if (sel == -1) buildBg() else design.items.getOrNull(sel)?.let { buildEl(it, sel) }
        editorHost.removeAllViews()
        editorHost.addView(Ui.tabify(c, editor, editorTab) { editorTab = it })
    }

    private fun card(title: String): LinearLayout {
        editor.addView(Ui.section(c, title))
        val card = Ui.card(c)
        editor.addView(card)
        return card
    }

    private fun slider(parent: LinearLayout, label: String, min: Int, max: Int, value: Int, show: (Int) -> String, set: (Int) -> Unit) {
        Ui.slider(c, parent, label, max - min, value - min, { show(it + min) }) { set(it + min); save() }
    }

    private fun toggle(parent: LinearLayout, label: String, value: Boolean, set: (Boolean) -> Unit) {
        Ui.toggle(c, parent, label, value) { set(it); save() }
    }

    private fun colors(parent: LinearLayout, label: String, value: Int, set: (Int) -> Unit) {
        parent.addView(Ui.label(c, label))
        parent.addView(Ui.colors(c, value or 0xFF000000.toInt()) { set(it); save() })
    }

    private fun choice(parent: LinearLayout, label: String?, items: List<String>, value: Int, set: (Int) -> Unit) {
        if (label != null) parent.addView(Ui.label(c, label))
        parent.addView(Ui.choice(c, items, value) { set(it); save() }.view)
    }

    private fun input(parent: LinearLayout, value: String, multi: Boolean, set: (String) -> Unit) {
        val et = EditText(c).apply {
            setText(value)
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            textSize = 15f
            background = Ui.rounded(c, Ui.SURFACE2, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = if (multi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) {}
                override fun afterTextChanged(s: Editable?) { set(s?.toString() ?: ""); save() }
            })
        }
        parent.addView(et, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(6) })
    }

    private fun buildBg() {
        val bg = design.bg
        val k = card("Background")
        choice(k, null, listOf("Photo", "Gradient", "Solid color"), bg.type) { bg.type = it }
        k.addView(Ui.button(c, "Choose photo", false) { a.pickCreatorPhoto() },
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        k.addView(Ui.label(c, "You can also send any wallpaper here from the Packs or Edit tab with \"🧩 Use in Creator\"."))
        colors(k, "Color 1", bg.c1) { bg.c1 = it }
        colors(k, "Color 2 (gradient)", bg.c2) { bg.c2 = it }
        slider(k, "Angle", 0, 360, bg.angle, { "$it°" }) { bg.angle = it }

        val t = card("Lock → Home effect")
        t.addView(Ui.label(c, "How the background changes when you unlock"))
        slider(t, "Dim on lock", 0, 90, bg.dimLock, { "$it%" }) { bg.dimLock = it }
        slider(t, "Dim on home", 0, 90, bg.dimHome, { "$it%" }) { bg.dimHome = it }
        slider(t, "Zoom on home", 0, 30, bg.zoomHome, { "$it%" }) { bg.zoomHome = it }
        toggle(t, "Blur on home", bg.blurHome) { bg.blurHome = it }
    }

    private fun buildEl(el: TextEl, i: Int) {
        // content
        val k = card(when (el.kind) { TextEl.KIND_TIME -> "Clock"; TextEl.KIND_DATE -> "Date"; else -> "Text" })
        when (el.kind) {
            TextEl.KIND_TIME -> {
                toggle(k, "24-hour", el.use24) { el.use24 = it }
                toggle(k, "Show seconds", el.seconds) { el.seconds = it }
                toggle(k, "Show AM/PM", el.ampm) { el.ampm = it }
                toggle(k, "Leading zero (09:05)", el.leadZero) { el.leadZero = it }
                toggle(k, "Stacked (hours above minutes)", el.stacked) { el.stacked = it }
            }
            TextEl.KIND_DATE -> {
                val idx = CreatorRenderer.DATE_PATTERNS.indexOf(el.datePattern).coerceAtLeast(-1)
                val labels = CreatorRenderer.DATE_PATTERNS.map {
                    try { java.text.SimpleDateFormat(it, java.util.Locale.getDefault()).format(java.util.Date()) } catch (e: Exception) { it }
                }
                choice(k, "Format", labels, if (idx < 0) 0 else idx) { el.datePattern = CreatorRenderer.DATE_PATTERNS[it] }
                k.addView(Ui.label(c, "Or type your own pattern (e.g. EEE · dd MMM)"))
                input(k, el.datePattern, false) { el.datePattern = it.ifBlank { "EEEE" } }
            }
            else -> {
                k.addView(Ui.label(c, "Your text (new line = Enter)"))
                input(k, el.text, true) { el.text = it }
            }
        }
        k.addView(Ui.label(c, "Layer name"))
        input(k, el.label, false) { el.label = it.ifBlank { "Layer" }; buildLayerRowLater() }
        k.addView(Ui.label(c, "Show on"))
        toggle(k, "Lock screen", el.showLock) { el.showLock = it; preview.state.go(preview.state.stage, design, now()) }
        toggle(k, "Home screen", el.showHome) { el.showHome = it; preview.state.go(preview.state.stage, design, now()) }
        k.addView(Ui.button(c, "Delete this layer", false) { delete(i) },
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })

        // font
        val f = card("Font")
        choice(f, null, CreatorRenderer.FONT_LABELS, el.font) { el.font = it; el.customFont = null }
        toggle(f, "Bold", el.bold) { el.bold = it }
        toggle(f, "Italic", el.italic) { el.italic = it }
        val fontRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fontRow.addView(Ui.pill(c, "📂 Import font file") { a.pickFont() })
        if (el.customFont != null) {
            fontRow.addView(Ui.text(c, "  " + File(el.customFont!!).name, 12f, Ui.MUTED), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            fontRow.addView(Ui.pill(c, "✕") { el.customFont = null; save(); buildEditor() })
        }
        f.addView(fontRow, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(6) })
        slider(f, "Size", 2, 45, el.size.toInt(), { "$it" }) { el.size = it.toFloat() }
        slider(f, "Spacing", -10, 60, el.spacing, { "$it" }) { el.spacing = it }
        slider(f, "Opacity", 10, 100, el.opacity, { "$it%" }) { el.opacity = it }
        choice(f, "Align", listOf("Left", "Center", "Right"), el.align) { el.align = it }

        // color
        val col = card("Color")
        colors(col, "Text color", el.color) { el.color = it }
        toggle(col, "Gradient", el.gradient) { el.gradient = it }
        colors(col, "Gradient end color", el.color2) { el.color2 = it }

        // effects
        val fx = card("Effects")
        toggle(fx, "Shadow", el.shadow) { el.shadow = it }
        slider(fx, "Shadow blur", 0, 40, el.shadowBlur, { "$it" }) { el.shadowBlur = it }
        slider(fx, "Shadow drop", 0, 30, el.shadowDy, { "$it" }) { el.shadowDy = it }
        colors(fx, "Shadow color", el.shadowColor) { el.shadowColor = (it and 0x00FFFFFF) or 0xAA000000.toInt() }
        fx.addView(Ui.label(c, "3D"))
        slider(fx, "3D depth", 0, 30, el.depth, { "$it" }) { el.depth = it }
        slider(fx, "3D angle", 0, 360, el.depthAngle, { "$it°" }) { el.depthAngle = it }
        colors(fx, "3D color", el.depthColor) { el.depthColor = it }
        slider(fx, "Outline", 0, 10, el.outline, { "$it" }) { el.outline = it }
        colors(fx, "Outline color", el.outlineColor) { el.outlineColor = it }
        slider(fx, "Glow", 0, 100, el.glow, { "$it" }) { el.glow = it }
        colors(fx, "Glow color", el.glowColor) { el.glowColor = it }

        // animation
        val an = card("Animation")
        choice(an, "Always (idle)", CreatorRenderer.IDLES, el.idle) { el.idle = it }
        choice(an, "In (when it appears)", CreatorRenderer.ANIMS, el.inAnim) { el.inAnim = it; replay(i) }
        choice(an, "Out (when it leaves)", CreatorRenderer.ANIMS, el.outAnim) { el.outAnim = it }
        slider(an, "Duration", 100, 2500, el.duration, { "${it}ms" }) { el.duration = it }
        slider(an, "Delay", 0, 1500, el.delay, { "${it}ms" }) { el.delay = it }
        an.addView(Ui.pill(c, "▶ Play in-animation") { replay(i) },
            LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { topMargin = dp(8) })

        // position
        val pos = card("Position")
        pos.addView(Ui.label(c, "Or drag it on the preview"))
        slider(pos, "Left ↔ right", 0, 100, (el.x * 100).toInt(), { "$it" }) { el.x = it / 100f }
        slider(pos, "Top ↕ bottom", 0, 100, (el.y * 100).toInt(), { "$it" }) { el.y = it / 100f }
    }

    private fun replay(i: Int) {
        preview.state.replay(i, design, now())
        preview.invalidate()
    }

    private fun buildLayerRowLater() = layerRow.post { buildLayerRow() }

    // ---------------- files ----------------
    fun reloadBg() {
        Thread {
            val cache = CreatorRenderer.BgCache()
            CreatorRenderer.loadBg(c, cache, p.getLong("creator_bg_ver", 0L), 720)
            a.runOnUiThread {
                preview.cache.photo = cache.photo
                preview.cache.blurred = cache.blurred
                preview.invalidate()
            }
        }.start()
    }

    /** Saves a finished full-screen picture as the Creator background. Call off the main thread. */
    fun saveBackground(bmp: Bitmap) {
        FileOutputStream(Design.photoFile(c)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        design.bg.type = 0
        design.save(p)
        p.edit().putLong("creator_bg_ver", System.currentTimeMillis()).apply()
        a.runOnUiThread {
            reloadBg()
            if (sel == -1) buildEditor()
        }
    }

    fun onPhotoPicked(uri: Uri) {
        a.toast("Loading photo…")
        Thread {
            try {
                val src = ImageLoader.load(c, uri, a.screenW, a.screenH)
                val out = ImageProcessor.render(src, ImageLoader.centerCrop(src, a.screenW.toFloat() / a.screenH), a.screenW, a.screenH, ImageProcessor.Settings(), hq = true)
                src.recycle()
                saveBackground(out)
                out.recycle()
            } catch (e: Throwable) {
                a.runOnUiThread { a.toast("Couldn't open photo: ${e.message}") }
            }
        }.start()
    }

    fun onFontPicked(uri: Uri) {
        val el = design.items.getOrNull(sel) ?: run { a.toast("Select a text layer first"); return }
        try {
            var name = "font_${System.currentTimeMillis()}.ttf"
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { n -> name = n.replace(Regex("[^A-Za-z0-9._-]"), "_") }
            }
            val dest = File(Design.fontsDir(c), name)
            c.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(dest).use { input.copyTo(it) } }
            val test = android.graphics.Typeface.createFromFile(dest)
            if (test == null) throw Exception("not a font")
            el.customFont = dest.path
            save()
            buildEditor()
            a.toast("Font added ✓")
        } catch (e: Throwable) {
            a.toast("That file isn't a font (.ttf or .otf)")
        }
    }

    private fun setLive() {
        design.save(p)
        val comp = ComponentName(c, CreatorWallpaperService::class.java)
        try {
            a.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp))
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                a.toast("Choose \"MyWallpaper Creator\" from the list")
            } catch (e2: Exception) {
                a.toast("This phone doesn't allow live wallpapers")
            }
        }
    }
}
