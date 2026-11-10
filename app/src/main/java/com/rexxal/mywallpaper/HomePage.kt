package com.rexxal.mywallpaper

import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.graphics.BitmapFactory
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home: what's on your phone right now, Jarvis status, quick actions, latest catches. */
class HomePage(private val a: MainActivity) {
    private val c = a
    private val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 24)) }
    val view = ScrollView(c).apply { addView(col); isVerticalScrollBarEnabled = false }

    private fun dp(v: Int) = Ui.dp(c, v)

    fun refresh() {
        col.removeAllViews()
        // current wallpaper
        col.addView(Ui.section(c, "On your screen"))
        val svc = try { WallpaperManager.getInstance(c).wallpaperInfo?.serviceName } catch (e: Exception) { null }
        val (title, sub, target) = when {
            svc == null -> Triple("Still picture", "A photo or pack wallpaper", MainActivity.WALLS)
            svc.endsWith("ClockWallpaperService") -> Triple("Robot", "Live wallpaper · your buddy's room", MainActivity.ROBOT)
            svc.endsWith("CreatorWallpaperService") -> Triple("Creator", "Your own lock & home design", MainActivity.CREATOR)
            svc.endsWith("JarvisWallpaperService") -> Triple("Jarvis", "Hacker robot live wallpaper", MainActivity.JARVIS)
            else -> Triple("Another app's wallpaper", "Not from MyWallpaper", MainActivity.FEATURES)
        }
        val wl = Ui.list(c)
        Ui.addRows(c, wl, listOf(Ui.row(c, '', title, sub, Ui.icon(c, '', 12f, Ui.MUTED), true) { a.show(target) }))
        col.addView(wl)

        // jarvis
        col.addView(Ui.section(c, "Jarvis"))
        val admin = try { c.getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(c, JarvisAdmin::class.java)) } catch (e: Exception) { false }
        val run = JarvisService.running()
        val photos = JarvisCamera.logDir(c).listFiles()?.filter { it.extension == "jpg" && !it.name.contains("_check") && !it.name.contains("_owner") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val jl = Ui.list(c)
        Ui.addRows(c, jl, listOf(
            Ui.row(c, '', if (run) "Watching" else "Off", if (run && admin) "Wrong PIN photos are on" else if (run) "Turn on wrong-PIN catching" else "Tap to set it up", Ui.value(c, if (run) "ON" else "OFF"), true) { a.show(MainActivity.JARVIS) },
            Ui.row(c, '', "Photos caught", if (photos.isEmpty()) "Nobody yet" else "Last: " + SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault()).format(Date(photos[0].lastModified())), Ui.value(c, photos.size.toString()), true) { a.show(MainActivity.JARVIS) }))
        col.addView(jl)
        if (photos.isNotEmpty()) {
            val strip = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            photos.take(4).forEach { f ->
                val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = Ui.outlined(c, Ui.SURFACE2, Ui.RULE, 10); clipToOutline = true; setOnClickListener { a.show(MainActivity.JARVIS) } }
                Thread { val b = try { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 }) } catch (e: Throwable) { null }; a.runOnUiThread { img.setImageBitmap(b) } }.start()
                strip.addView(img, LinearLayout.LayoutParams(0, dp(84), 1f).apply { rightMargin = dp(8) })
            }
            col.addView(strip)
        }

        // quick actions
        col.addView(Ui.section(c, "Quick"))
        val ql = Ui.list(c)
        Ui.addRows(c, ql, listOf(
            Ui.row(c, '', "New wallpaper", "Browse generated packs", null, true) { a.show(MainActivity.WALLS) },
            Ui.row(c, '', "Edit a photo", "Pick from your gallery", null, true) { a.pickImage() },
            Ui.row(c, '', "Robot", "Tune your live buddy", null, true) { a.show(MainActivity.ROBOT) },
            Ui.row(c, '', "Creator", "Design your lock screen", null, true) { a.show(MainActivity.CREATOR) }))
        col.addView(ql)
    }

    init { refresh() }
}
