package com.rexxal.mywallpaper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        const val HOME = 0; const val FEATURES = 1; const val JARVIS = 2; const val PROFILE = 3
        const val WALLS = 10; const val ROBOT = 11; const val CREATOR = 12
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
    private lateinit var home: HomePage
    private lateinit var profile: ProfilePage
    private val screens = LinkedHashMap<Int, View>()
    private val navItems = mutableListOf<Triple<TextView, TextView, Int>>()
    private var current = -1
    private lateinit var wallsChoice: Ui.Choice

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        if (!Ui.DARK) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val size = Ui.screenSize(this)
        screenW = size.first
        screenH = size.second
        val p = Ui.prefs(this)
        if (p.getLong("first_use", 0L) == 0L) p.edit().putLong("first_use", System.currentTimeMillis()).apply()
        setContentView(buildUi())
        AutoChanger.schedule(this)
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    fun toggleTheme() {
        Ui.prefs(this).edit().putBoolean("ui_dark", !Ui.DARK).putInt("ui_last_screen", current).apply()
        recreate()
    }

    private fun screen(kick: String, title: String, sub: String, body: View, back: Boolean): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (back) {
            val b = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), dp(12), 0); setOnClickListener { show(FEATURES) } }
            b.addView(Ui.icon(this, '\uf060', 12f, Ui.TEXT))
            b.addView(Ui.text(this, "  Features", 13f, Ui.TEXT).apply { typeface = Ui.sansMed })
            col.addView(b, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP))
        }
        col.addView(Ui.header(this, kick, title, sub, Ui.iconButton(this, if (Ui.DARK) '\uf185' else '\uf186', "Switch light and dark") { toggleTheme() }))
        col.addView(body, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        return col
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        packs = PacksPage(this); edit = EditPage(this); auto = AutoPage(this)
        live = LivePage(this); creator = CreatorPage(this); jarvis = JarvisPage(this)
        home = HomePage(this); profile = ProfilePage(this)

        val wallsHost = FrameLayout(this)
        listOf(packs.view, edit.view, auto.view).forEach { wallsHost.addView(it, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH)) }
        val walls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wallsChoice = Ui.choice(this, listOf("🖼 Packs", "📷 Photo", "🔄 Auto"), 0) { showWalls(it) }
        walls.addView(wallsChoice.view)
        walls.addView(wallsHost, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        showWalls(0)

        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val today = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date())
        val greet = if (h < 5) "Good night" else if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
        screens[HOME] = screen(today, greet, "Everything on your phone, at a glance.", home.view, false)
        screens[FEATURES] = screen("Everything except Jarvis", "Features", "Pick what you want to make.", buildFeatures(), false)
        screens[JARVIS] = screen("Security", "Jarvis", "Catches whoever opens your lock screen.", jarvis.view, false)
        screens[PROFILE] = screen("Your account", "Profile", "About you, the app and your data.", profile.view, false)
        screens[WALLS] = screen("Features", "Wallpapers", "Pick one, edit a photo, or let it change itself.", walls, true)
        screens[ROBOT] = screen("Live wallpaper", "Robot", "Your little buddy and his room.", live.view, true)
        screens[CREATOR] = screen("Lock & home", "Creator", "Design your own clock, date and text.", creator.view, true)

        val content = FrameLayout(this).apply { setPadding(dp(18), 0, dp(18), dp(6)) }
        screens.values.forEach { content.addView(it, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH)) }
        root.addView(content, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))

        root.addView(View(this).apply { setBackgroundColor(Ui.RULE) }, LinearLayout.LayoutParams(Ui.MATCH, dp(1)))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.BG); setPadding(dp(6), dp(8), dp(6), dp(10)) }
        listOf(Triple('\uf015', "Home", HOME), Triple('\uf61f', "Features", FEATURES), Triple('\uf21b', "Jarvis", JARVIS), Triple('\uf007', "Profile", PROFILE)).forEach { (g, label, id) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); show(id) }
            }
            val ic = Ui.icon(this, g, 15f, Ui.MUTED).apply { gravity = Gravity.CENTER }
            item.addView(ic, LinearLayout.LayoutParams(dp(52), dp(30)))
            val lb = Ui.text(this, label, 11f, Ui.MUTED).apply { typeface = Ui.sansMed; gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) }
            item.addView(lb)
            navItems += Triple(ic, lb, id)
            bar.addView(item, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP))

        val p = Ui.prefs(this)
        val last = p.getInt("ui_last_screen", HOME)
        p.edit().remove("ui_last_screen").apply()
        show(if (screens.containsKey(last)) last else HOME)
        return root
    }

    private fun buildFeatures(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(20)) }
        val list = Ui.list(this)
        Ui.addRows(this, list, listOf(
            Ui.row(this, '\uf5fd', "Wallpaper packs", "Generated designs, new every tap", null, true) { show(WALLS); showWalls(0) },
            Ui.row(this, '\uf03e', "Photo editor", "Crop, filters, blur, dim", null, true) { show(WALLS); showWalls(1) },
            Ui.row(this, '\uf2f1', "Auto change", "New wallpaper on a schedule", null, true) { show(WALLS); showWalls(2) },
            Ui.row(this, '\uf544', "Robot", "Live wallpaper with your buddy", null, true) { show(ROBOT) },
            Ui.row(this, '\uf5ad', "Creator", "Your own clock, date and text", null, true) { show(CREATOR) }))
        col.addView(list)
        return android.widget.ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
    }

    private fun showWalls(i: Int) {
        wallsChoice.select(i)
        listOf(packs.view, edit.view, auto.view).forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE }
    }

    fun show(id: Int) {
        current = id
        screens.forEach { (k, v) -> v.visibility = if (k == id) View.VISIBLE else View.GONE }
        val navId = if (id >= 10) FEATURES else id
        navItems.forEach { (ic, lb, nid) ->
            val sel = nid == navId
            ic.background = if (sel) Ui.rounded(this, Ui.ACCENT, 999) else null
            ic.setTextColor(if (sel) Ui.ON_ACCENT else Ui.MUTED)
            lb.setTextColor(if (sel) Ui.TEXT else Ui.MUTED)
        }
        when (id) { ROBOT -> live.refresh(); CREATOR -> creator.refresh(); JARVIS -> jarvis.refresh(); HOME -> home.refresh(); PROFILE -> profile.refresh() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            current >= 10 -> show(FEATURES)
            current != HOME -> show(HOME)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
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

    fun showCreator() = show(CREATOR)

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
        show(WALLS); showWalls(1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        jarvis.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::jarvis.isInitialized) { jarvis.refresh(); home.refresh(); profile.refresh() }
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
