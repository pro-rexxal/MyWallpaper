package com.rexxal.mywallpaper

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_PICK = 1
        private const val REQ_FOLDER = 2
        private const val REQ_CREATOR_PHOTO = 3
        private const val REQ_FONT = 4
        private const val REQ_SELFIE = 5
    }

    var screenW = 1080
    var screenH = 2400

    private lateinit var packs: PacksPage
    private lateinit var edit: EditPage
    private lateinit var auto: AutoPage
    private lateinit var live: LivePage
    lateinit var creator: CreatorPage
    private lateinit var jarvis: JarvisPage
    private lateinit var pages: List<View>
    private val tabs = mutableListOf<TextView>()
    private lateinit var subtitle: TextView
    private val subtitles = listOf(
        "Wallpapers · tap one to edit and set it",
        "Photo · crop, filters and effects, then set",
        "Auto · change wallpaper on a schedule",
        "Robot · live wallpaper with your robot buddy",
        "Creator · design your own lock & home screen",
        "Jarvis · catches whoever opens your lock screen"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.SURFACE
        val size = Ui.screenSize(this)
        screenW = size.first
        screenH = size.second
        setContentView(buildUi())
        AutoChanger.schedule(this)
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }

        // header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(8))
        }
        header.addView(Ui.text(this, "MyWallpaper", 22f, Ui.TEXT, true))
        subtitle = Ui.text(this, subtitles[0], 13f, Ui.MUTED)
        header.addView(subtitle)
        root.addView(header)

        // pages
        packs = PacksPage(this)
        edit = EditPage(this)
        auto = AutoPage(this)
        live = LivePage(this)
        creator = CreatorPage(this)
        jarvis = JarvisPage(this)
        pages = listOf(packs.view, edit.view, auto.view, live.view, creator.view, jarvis.view)
        val content = FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), dp(10)) }
        pages.forEach { content.addView(it, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH)) }
        root.addView(content, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        // bottom tabs
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.SURFACE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        listOf("✨\nWallpapers", "🎨\nPhoto", "🔄\nAuto", "🤖\nRobot", "🧩\nCreator", "🕵\nJarvis").forEachIndexed { i, label ->
            val tv = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { showTab(i) }
            }
            tabs += tv
            bar.addView(tv, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            })
        }
        root.addView(bar, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))

        showTab(0)
        return root
    }

    private fun showTab(i: Int) {
        pages.forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { k, tv ->
            val sel = k == i
            tv.background = if (sel) Ui.rounded(this, Ui.SURFACE2, 14) else null
            tv.setTextColor(if (sel) Ui.TEXT else Ui.MUTED)
        }
        subtitle.text = subtitles[i]
        if (i == 3) live.refresh()
        if (i == 4) creator.refresh()
        if (i == 5) jarvis.refresh()
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    fun pickImage() {
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

    fun pickFolder() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER)
        } catch (e: Exception) {
            toast("Folder picker not available")
        }
    }

    fun onLivePhotoChanged() = live.reloadPhoto()

    fun showCreator() = showTab(4)

    fun pickCreatorPhoto() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
        try { startActivityForResult(open, REQ_CREATOR_PHOTO) } catch (e: Exception) { toast("Photo picker not available") }
    }

    fun pickSelfie() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
        try { startActivityForResult(open, REQ_SELFIE) } catch (e: Exception) { toast("Photo picker not available") }
    }

    fun pickFont() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        try { startActivityForResult(open, REQ_FONT) } catch (e: Exception) { toast("File picker not available") }
    }

    fun openInEditor(bmp: android.graphics.Bitmap) {
        edit.setBitmap(bmp)
        showTab(1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        jarvis.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::jarvis.isInitialized) jarvis.refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK -> edit.setImage(uri)
            REQ_FOLDER -> auto.onFolderPicked(uri)
            REQ_CREATOR_PHOTO -> creator.onPhotoPicked(uri)
            REQ_FONT -> creator.onFontPicked(uri)
            REQ_SELFIE -> jarvis.onSelfie(uri)
        }
    }
}
