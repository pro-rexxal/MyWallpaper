package com.rexxal.mywallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_PICK = 1
        private val BG = 0xFF0E0F13.toInt()
        private val SURFACE = 0xFF1C1E25.toInt()
        private val ACCENT = 0xFF7C5CFF.toInt()
        private val TEXT = 0xFFEDEDF2.toInt()
        private val MUTED = 0xFF8A8D98.toInt()
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private lateinit var cropView: CropView
    private var source: Bitmap? = null
    private val settings = ImageProcessor.Settings()
    private val resetActions = mutableListOf<() -> Unit>()
    private val chips = mutableListOf<Pair<TextView, ImageProcessor.Filter>>()
    private var screenW = 1080
    private var screenH = 2400
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        readScreenSize()
        setContentView(buildUi())
    }

    // ---------- screen ----------

    private fun readScreenSize() {
        val wm = getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
        screenW = minOf(w, h)
        screenH = maxOf(w, h)
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 999) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "MyWallpaper"
            setTextColor(TEXT)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(TextView(this).apply {
            text = "Pick photo"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(ACCENT)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { pickImage() }
        })
        root.addView(header)

        // Preview
        cropView = CropView(this).apply {
            aspect = screenW.toFloat() / screenH
            onCropChanged = { refreshPreview() }
            setOnClickListener { if (source == null) pickImage() }
        }
        root.addView(cropView, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(8) })

        root.addView(TextView(this).apply {
            text = "Drag to move  ·  Pinch to zoom"
            setTextColor(MUTED)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(6))
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        // Filters
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ImageProcessor.Filter.values().forEach { f ->
            val chip = TextView(this).apply {
                text = f.label
                textSize = 13f
                setPadding(dp(14), dp(7), dp(14), dp(7))
                setOnClickListener {
                    settings.filter = f
                    updateChips()
                    refreshPreview()
                }
            }
            chips += chip to f
            chipRow.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8) })
        }
        updateChips()
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        // Sliders
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        val centered = { p: Int -> (p - 100).let { if (it > 0) "+$it" else "$it" } }
        addSlider(controls, "Brightness", 200, 100, centered) { settings.brightness = (it - 100) / 200f }
        addSlider(controls, "Contrast", 200, 100, centered) { settings.contrast = 0.5f + it / 200f }
        addSlider(controls, "Saturation", 200, 100, centered) { settings.saturation = it / 100f }
        addSlider(controls, "Warmth", 200, 100, centered) { settings.warmth = (it - 100) / 100f }
        addSlider(controls, "Blur", 100, 0, { "$it" }) { settings.blur = it }
        addSlider(controls, "Dim", 85, 0, { "$it%" }) { settings.dim = it / 100f }
        addSlider(controls, "Vignette", 100, 0, { "$it" }) { settings.vignette = it / 100f }
        resetActions += {
            settings.filter = ImageProcessor.Filter.NORMAL
            updateChips()
        }
        controls.addView(TextView(this).apply {
            text = "Reset all edits"
            setTextColor(ACCENT)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
            setOnClickListener {
                resetActions.forEach { it() }
                cropView.resetCrop()
                refreshPreview()
            }
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        root.addView(ScrollView(this).apply { addView(controls) }, LinearLayout.LayoutParams(MATCH, dp(190)))

        // Apply buttons
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val targets = listOf(
            Triple("Home", "home screen", WallpaperManager.FLAG_SYSTEM),
            Triple("Lock", "lock screen", WallpaperManager.FLAG_LOCK),
            Triple("Both", "home & lock", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        )
        targets.forEachIndexed { i, (label, longLabel, flags) ->
            val primary = i == targets.size - 1
            bottom.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (primary) Color.WHITE else TEXT)
                background = rounded(if (primary) ACCENT else SURFACE, 14)
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { applyWallpaper(flags, longLabel) }
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                if (i > 0) leftMargin = dp(8)
            })
        }
        root.addView(bottom, LinearLayout.LayoutParams(MATCH, WRAP))
        return root
    }

    private fun addSlider(
        parent: LinearLayout, label: String, maxValue: Int, def: Int,
        show: (Int) -> String, apply: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val name = TextView(this).apply { text = label; setTextColor(TEXT); textSize = 13f }
        val value = TextView(this).apply { setTextColor(MUTED); textSize = 12f; gravity = Gravity.END }
        val seek = SeekBar(this)
        seek.max = maxValue
        seek.progressTintList = ColorStateList.valueOf(ACCENT)
        seek.thumbTintList = ColorStateList.valueOf(ACCENT)
        seek.progressBackgroundTintList = ColorStateList.valueOf(0xFF3A3D47.toInt())
        seek.progress = def
        value.text = show(def)
        apply(def)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                value.text = show(p)
                apply(p)
                refreshPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        row.addView(name, LinearLayout.LayoutParams(dp(84), WRAP))
        row.addView(seek, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(value, LinearLayout.LayoutParams(dp(40), WRAP))
        parent.addView(row)
        resetActions += { seek.progress = def }
    }

    private fun updateChips() {
        chips.forEach { (tv, f) ->
            val selected = f == settings.filter
            tv.background = rounded(if (selected) ACCENT else SURFACE)
            tv.setTextColor(if (selected) Color.WHITE else TEXT)
        }
    }

    private fun refreshPreview() {
        val s = source ?: return
        val fw = cropView.frameW
        val fh = cropView.frameH
        if (fw <= 0 || fh <= 0) return
        cropView.preview = ImageProcessor.render(s, cropView.crop, fw, fh, settings, hq = false)
        cropView.invalidate()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------- picking & loading ----------

    private fun pickImage() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(open, REQ_PICK)
        } catch (e: Exception) {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_PICK)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { loadImage(it) }
        }
    }

    private fun loadImage(uri: Uri) {
        Thread {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) throw Exception("not an image")

                // Keep enough detail for zooming, but don't run out of memory.
                var sample = 1
                val tw = screenW * 3 / 2
                val th = screenH * 3 / 2
                while (bounds.outWidth / (sample * 2) >= tw && bounds.outHeight / (sample * 2) >= th) sample *= 2
                while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 12_000_000L) sample *= 2

                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: throw Exception("can't read image")

                // Fix sideways phone photos.
                val rotation = try {
                    contentResolver.openInputStream(uri)?.use {
                        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    } ?: 0f
                } catch (e: Exception) { 0f }
                if (rotation != 0f) {
                    val m = Matrix().apply { postRotate(rotation) }
                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    if (rotated !== bmp) bmp.recycle()
                    bmp = rotated
                }

                runOnUiThread {
                    source = bmp
                    cropView.setImage(bmp)
                }
            } catch (e: Throwable) {
                runOnUiThread { toast("Couldn't open photo: ${e.message}") }
            }
        }.start()
    }

    // ---------- applying ----------

    private fun applyWallpaper(flags: Int, label: String) {
        val src = source
        if (src == null) {
            toast("Pick a photo first")
            return
        }
        if (busy) return
        busy = true
        toast("Applying…")
        val crop = RectF(cropView.crop)
        val s = settings.copy()
        Thread {
            try {
                val out = ImageProcessor.render(src, crop, screenW, screenH, s, hq = true)
                WallpaperManager.getInstance(this@MainActivity).setBitmap(out, null, true, flags)
                out.recycle()
                runOnUiThread { toast("Done! Set on $label ✓") }
            } catch (e: Throwable) {
                runOnUiThread { toast("Failed: ${e.message}") }
            } finally {
                runOnUiThread { busy = false }
            }
        }.start()
    }
}
