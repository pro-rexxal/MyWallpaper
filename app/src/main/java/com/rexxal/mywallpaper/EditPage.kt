package com.rexxal.mywallpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import java.io.FileOutputStream

/** Tab 1: pick a photo, crop, add effects, set it. */
class EditPage(private val a: MainActivity) {

    private val c = a
    private fun dp(v: Int) = Ui.dp(c, v)

    private var source: Bitmap? = null
    private val settings = ImageProcessor.Settings()
    private val resetActions = mutableListOf<() -> Unit>()
    private lateinit var cropView: CropView
    private lateinit var filterChoice: Ui.Choice
    private var busy = false

    val view: LinearLayout = build()

    private fun build(): LinearLayout {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        cropView = CropView(c).apply {
            aspect = a.screenW.toFloat() / a.screenH
            onCropChanged = { refreshPreview() }
            setOnClickListener { if (source == null) a.pickImage() }
        }
        root.addView(cropView, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        root.addView(Ui.text(c, "Drag to move  ·  Pinch to zoom", 12f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))

        // quick actions
        val actions = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        listOf<Pair<String, () -> Unit>>(
            "📷  Photo" to { a.pickImage() },
            "✨  Use in Live" to { sendToLive() },
            "💾  Save" to { saveToGallery() },
            "↺  Reset" to { reset() }
        ).forEach { (l, f) ->
            actions.addView(Ui.pill(c, l, f), LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(8) })
        }
        root.addView(HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(actions)
        }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))

        filterChoice = Ui.choice(c, ImageProcessor.Filter.values().map { it.label }, 0) {
            settings.filter = ImageProcessor.Filter.values()[it]
            refreshPreview()
        }
        root.addView(filterChoice.view, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(4) })

        val controls = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val centered = { p: Int -> (p - 100).let { if (it > 0) "+$it" else "$it" } }
        addSlider(controls, "Brightness", 200, 100, centered) { settings.brightness = (it - 100) / 200f }
        addSlider(controls, "Contrast", 200, 100, centered) { settings.contrast = 0.5f + it / 200f }
        addSlider(controls, "Saturation", 200, 100, centered) { settings.saturation = it / 100f }
        addSlider(controls, "Warmth", 200, 100, centered) { settings.warmth = (it - 100) / 100f }
        addSlider(controls, "Blur", 100, 0, { "$it" }) { settings.blur = it }
        addSlider(controls, "Dim", 85, 0, { "$it%" }) { settings.dim = it / 100f }
        addSlider(controls, "Vignette", 100, 0, { "$it" }) { settings.vignette = it / 100f }
        root.addView(ScrollView(c).apply { addView(controls) }, LinearLayout.LayoutParams(Ui.MATCH, dp(150)))

        val bottom = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val targets = listOf(
            Triple("Home", "home screen", WallpaperManager.FLAG_SYSTEM),
            Triple("Lock", "lock screen", WallpaperManager.FLAG_LOCK),
            Triple("Both", "home & lock", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        )
        targets.forEachIndexed { i, (label, long, flags) ->
            bottom.addView(Ui.button(c, label, i == 2) { setWallpaper(flags, long) },
                LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        root.addView(bottom, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        return root
    }

    private fun addSlider(parent: LinearLayout, label: String, max: Int, def: Int, show: (Int) -> String, set: (Int) -> Unit) {
        set(def)
        val seek: SeekBar = Ui.slider(c, parent, label, max, def, show) { set(it); refreshPreview() }
        resetActions += { seek.progress = def }
    }

    private fun reset() {
        resetActions.forEach { it() }
        settings.filter = ImageProcessor.Filter.NORMAL
        filterChoice.select(0)
        cropView.resetCrop()
        refreshPreview()
    }

    fun setImage(uri: Uri) {
        Thread {
            try {
                val bmp = ImageLoader.load(c, uri, a.screenW, a.screenH)
                a.runOnUiThread {
                    source = bmp
                    cropView.setImage(bmp)
                }
            } catch (e: Throwable) {
                a.runOnUiThread { a.toast("Couldn't open photo: ${e.message}") }
            }
        }.start()
    }

    /** Opens a ready-made bitmap (e.g. from the Packs tab). */
    fun setBitmap(bmp: Bitmap) {
        source = bmp
        cropView.setImage(bmp)
    }

    private fun refreshPreview() {
        val s = source ?: return
        val fw = cropView.frameW
        val fh = cropView.frameH
        if (fw <= 0 || fh <= 0) return
        cropView.preview = ImageProcessor.render(s, cropView.crop, fw, fh, settings, hq = false)
        cropView.invalidate()
    }

    /** Renders the final full-screen image off the main thread, then runs [done] with it. */
    private fun renderFinal(working: String, done: (Bitmap) -> String) {
        val src = source
        if (src == null) {
            a.toast("Pick a photo first")
            return
        }
        if (busy) return
        busy = true
        a.toast(working)
        val crop = RectF(cropView.crop)
        val s = settings.copy()
        ImageLoader.saveStyle(Ui.prefs(c), s)
        Thread {
            try {
                val out = ImageProcessor.render(src, crop, a.screenW, a.screenH, s, hq = true)
                val msg = done(out)
                out.recycle()
                a.runOnUiThread { a.toast(msg) }
            } catch (e: Throwable) {
                a.runOnUiThread { a.toast("Failed: ${e.message}") }
            } finally {
                a.runOnUiThread { busy = false }
            }
        }.start()
    }

    private fun setWallpaper(flags: Int, label: String) = renderFinal("Applying…") { out ->
        WallpaperManager.getInstance(c).setBitmap(out, null, true, flags)
        "Done! Set on $label ✓"
    }

    private fun sendToLive() = renderFinal("Sending to Live…") { out ->
        FileOutputStream(LiveRenderer.photoFile(c)).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val p = Ui.prefs(c)
        val cfg = LiveConfig.load(p)
        cfg.bgMode = 0
        cfg.save(p)
        p.edit().putLong("live_bg_ver", System.currentTimeMillis()).apply()
        a.runOnUiThread { a.onLivePhotoChanged() }
        "Photo sent to the Live tab ✓"
    }

    private fun saveToGallery() = renderFinal("Saving…") { out ->
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "MyWallpaper_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyWallpaper")
        }
        val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("can't create file")
        c.contentResolver.openOutputStream(uri)?.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        "Saved to Pictures/MyWallpaper ✓"
    }
}
