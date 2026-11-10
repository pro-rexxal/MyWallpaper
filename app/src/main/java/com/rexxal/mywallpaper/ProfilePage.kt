package com.rexxal.mywallpaper

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Profile: you, appearance, app info, support, privacy and legal. */
class ProfilePage(private val a: MainActivity) {
    private val c = a
    private val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 28)) }
    val view = ScrollView(c).apply { addView(col); isVerticalScrollBarEnabled = false }
    private val chev get() = Ui.icon(c, '', 12f, Ui.MUTED)

    private fun dp(v: Int) = Ui.dp(c, v)

    private fun version(): String = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }

    private fun info(title: String, body: String) {
        AlertDialog.Builder(c).setTitle(title).setMessage(body).setPositiveButton("OK", null).show()
    }

    private fun open(uri: String) { try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (e: Exception) { a.toast("Nothing can open that") } }

    fun refresh() {
        col.removeAllViews()
        // identity
        val me = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        me.addView(TextView(c).apply { text = "R"; textSize = 24f; typeface = Ui.sansBold; gravity = Gravity.CENTER; setTextColor(Ui.ON_ACCENT); background = Ui.rounded(c, Ui.ACCENT, 999) }, LinearLayout.LayoutParams(dp(60), dp(60)))
        val nm = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        nm.addView(Ui.text(c, "Rawal", 20f, Ui.TEXT, true))
        nm.addView(Ui.text(c, "Creator of MyWallpaper", 13f, Ui.MUTED))
        me.addView(nm)
        col.addView(me)

        val p = Ui.prefs(c)
        val days = ((System.currentTimeMillis() - p.getLong("first_use", System.currentTimeMillis())) / 86_400_000L + 1)
        val caught = JarvisCamera.logDir(c).listFiles()?.count { it.extension == "jpg" && !it.name.contains("_check") && !it.name.contains("_owner") } ?: 0
        val stats = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        listOf(days.toString() to "days", caught.toString() to "caught", version() to "version").forEachIndexed { i, (v, l) ->
            val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; background = Ui.outlined(c, Ui.SURFACE, Ui.RULE, 12); setPadding(dp(12), dp(10), dp(12), dp(10)) }
            box.addView(Ui.text(c, v, 18f, Ui.TEXT, true))
            box.addView(Ui.kicker(c, l))
            stats.addView(box, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (i < 2) rightMargin = dp(8) })
        }
        col.addView(stats)

        fun group(name: String, rows: List<android.view.View>) {
            col.addView(Ui.section(c, name)); val l = Ui.list(c); Ui.addRows(c, l, rows); col.addView(l)
        }
        group("Appearance", listOf(Ui.row(c, if (Ui.DARK) '' else '', "Theme", if (Ui.DARK) "Dark paper" else "Light paper", Ui.value(c, if (Ui.DARK) "DARK" else "LIGHT"), true) { a.toggleTheme() }))
        group("App", listOf(
            Ui.row(c, '', "Rate MyWallpaper", "On the Play Store", chev, true) { open("market://details?id=${c.packageName}") },
            Ui.row(c, '', "Share the app", "Send it to a friend", chev, true) {
                try { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Try MyWallpaper: https://play.google.com/store/apps/details?id=${c.packageName}"), "Share")) } catch (e: Exception) {}
            },
            Ui.row(c, '', "What's new", "New paper look, Jarvis face check", chev, true) { info("What's new", "• New black & white paper design with light and dark mode\n• Home, Features, Jarvis and Profile tabs\n• Jarvis face check: knows when it's you\n• Quieter Jarvis notification") },
            Ui.row(c, '', "Version", null, Ui.value(c, version()), true, null)))
        group("Support", listOf(
            Ui.row(c, '', "Contact", "Write to the developer", chev, true) { open("mailto:?subject=MyWallpaper") },
            Ui.row(c, '', "Report a problem", "Tell us what went wrong", chev, true) { open("mailto:?subject=MyWallpaper%20problem%20(v${version()})") },
            Ui.row(c, '', "Help", "Lock screen and Jarvis tips", chev, true) { info("Help", "Lock screen not changing? Allow Autostart and set battery to 'No restrictions' for MyWallpaper.\n\nJarvis needs Camera and the device-admin permission to catch wrong PINs.\n\nThe robot reacts to taps on your home screen; the lock screen can't receive touches.") }))
        group("Privacy & data", listOf(
            Ui.row(c, '', "Privacy policy", "Nothing leaves your phone", chev, true) { info("Privacy policy", "MyWallpaper keeps everything on your device.\n\n• Jarvis photos and your face data are stored only inside the app and are never uploaded.\n• Weather uses your approximate city with Open-Meteo; no account or identifier is sent.\n• No ads, no analytics, no tracking.\n\nDeleting the app or using 'Delete all data' removes everything.") },
            Ui.row(c, '', "Permissions", "Camera, device admin, notifications", chev, true) {
                try { c.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))) } catch (e: Exception) {}
            },
            Ui.row(c, '', "Export settings", "Share your setup as text", chev, true) {
                val txt = Ui.prefs(c).all.entries.joinToString("\n") { "${it.key}=${it.value}" }
                try { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Export settings")) } catch (e: Exception) {}
            },
            Ui.row(c, '', "Delete all data", "Photos, faces, settings", chev, true) {
                AlertDialog.Builder(c).setTitle("Delete all data?").setMessage("Removes Jarvis photos, your saved face and all settings. This can't be undone.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                        c.filesDir.listFiles()?.forEach { it.deleteRecursively() }
                        Ui.prefs(c).edit().clear().apply()
                        a.toast("All data deleted"); a.recreate()
                    }.show()
            }))
        group("Legal", listOf(
            Ui.row(c, '', "Terms of use", null, chev, true) { info("Terms of use", "MyWallpaper is provided as is. Use Jarvis only on your own phone and follow your local laws about taking photos of others.") },
            Ui.row(c, '', "Open-source licenses", null, chev, true) { info("Open-source licenses", "Font Awesome Free — SIL OFL 1.1 / CC BY 4.0\nIBM Plex — SIL OFL 1.1\nFaceNet model — MIT\nGoogle ML Kit — ML Kit Terms\nTensorFlow Lite — Apache 2.0\nOpen-Meteo — CC BY 4.0") }))
        col.addView(Ui.text(c, "Made with care by Rawal · India", 12f, Ui.FAINT).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0) })
    }

    init { refresh() }
}
