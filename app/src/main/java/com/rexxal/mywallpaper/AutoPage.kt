package com.rexxal.mywallpaper

import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Tab 2: change the wallpaper automatically from a folder. */
class AutoPage(private val a: MainActivity) {

    private val c = a
    private val p = Ui.prefs(c)
    private fun dp(v: Int) = Ui.dp(c, v)
    private lateinit var folderText: TextView
    private lateinit var statusText: TextView

    val view: ScrollView = build()

    private fun build(): ScrollView {
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        col.addView(Ui.section(c, "Photo folder"))
        val folderCard = Ui.card(c)
        folderText = Ui.text(c, "No folder chosen yet", 15f, Ui.TEXT, true)
        folderCard.addView(folderText)
        folderCard.addView(Ui.label(c, "Put the photos you want to rotate in one folder on your phone."))
        folderCard.addView(Ui.button(c, "Choose folder", false) { a.pickFolder() },
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(10) })
        col.addView(folderCard)

        col.addView(Ui.section(c, "Schedule"))
        val sched = Ui.card(c)
        Ui.toggle(c, sched, "Auto-change on", p.getBoolean("auto_on", false)) {
            p.edit().putBoolean("auto_on", it).apply()
            AutoChanger.schedule(c)
            if (it && p.getString("auto_folder", null) == null) a.toast("Choose a folder first")
        }
        sched.addView(Ui.label(c, "Change every"))
        sched.addView(Ui.choice(c, AutoChanger.INTERVAL_LABELS, p.getInt("auto_interval", 2)) {
            p.edit().putInt("auto_interval", it).apply()
            AutoChanger.schedule(c)
        }.view)
        sched.addView(Ui.label(c, "Set on"))
        sched.addView(Ui.choice(c, listOf("Home", "Lock", "Both"), p.getInt("auto_target", 2)) {
            p.edit().putInt("auto_target", it).apply()
        }.view)
        Ui.toggle(c, sched, "Shuffle (random order)", p.getBoolean("auto_shuffle", true)) {
            p.edit().putBoolean("auto_shuffle", it).apply()
        }
        Ui.toggle(c, sched, "Apply my Edit style (filter, blur, dim…)", p.getBoolean("auto_style", false)) {
            p.edit().putBoolean("auto_style", it).apply()
        }
        col.addView(sched)

        col.addView(Ui.button(c, "Change now", true) { changeNow() },
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(16) })
        statusText = Ui.text(c, "", 13f, Ui.MUTED).apply { setPadding(dp(4), dp(8), dp(4), 0) }
        col.addView(statusText)

        col.addView(Ui.section(c, "Keep it running"))
        val tip = Ui.card(c)
        tip.addView(Ui.text(c,
            "Realme and Xiaomi phones stop background apps to save battery. If changes stop happening, open " +
                "Settings → Apps → MyWallpaper → Battery and allow background activity (or set it to \"No restrictions\").",
            13f, Ui.MUTED))
        tip.addView(Ui.button(c, "Open app settings", false) {
            a.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + c.packageName)))
        }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(10) })
        col.addView(tip)

        refreshFolder()
        return ScrollView(c).apply { addView(col) }
    }

    fun onFolderPicked(uri: Uri) {
        try {
            c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        p.edit().putString("auto_folder", uri.toString()).putInt("auto_index", -1).apply()
        AutoChanger.schedule(c)
        refreshFolder()
    }

    private fun refreshFolder() {
        Thread {
            val name = AutoChanger.folderName(c)
            val count = try { AutoChanger.listImages(c).size } catch (e: Exception) { 0 }
            a.runOnUiThread {
                folderText.text = if (name == null) "No folder chosen yet" else "📁  $name  ·  $count photos"
            }
        }.start()
    }

    private fun changeNow() {
        statusText.text = "Changing…"
        Thread {
            val msg = try { AutoChanger.changeNow(c) } catch (e: Throwable) { "Failed: ${e.message}" }
            a.runOnUiThread { statusText.text = msg }
        }.start()
    }
}
