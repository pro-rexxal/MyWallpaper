package com.rexxal.mywallpaper

import android.Manifest
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jarvis Mode tab: preview, setup, settings and the private photo log. */
class JarvisPage(private val a: MainActivity) {
    private val c = a
    private val p = Ui.prefs(c)
    private fun dp(v: Int) = Ui.dp(c, v)
    private val jr = JarvisRenderer().also { JarvisSettings.apply(p, it) }
    private lateinit var preview: View
    private lateinit var status: TextView
    private lateinit var logList: LinearLayout
    private lateinit var faceCount: TextView

    val view: LinearLayout = build()

    inner class Preview : View(a) {
        private val frame = android.graphics.RectF(); private val clip = android.graphics.Path()
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            val asp = a.screenW.toFloat() / a.screenH; var fh = h - dp(8f); var fw = fh * asp
            if (fw > w - dp(8f)) { fw = w - dp(8f); fh = fw / asp }
            frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2); clip.reset(); clip.addRoundRect(frame, dp(16f), dp(16f), android.graphics.Path.Direction.CW)
        }
        private fun dp(v: Float) = v * resources.displayMetrics.density
        override fun onDraw(cv: Canvas) {
            cv.save(); cv.clipPath(clip); cv.translate(frame.left, frame.top); jr.draw(cv, frame.width(), frame.height(), System.currentTimeMillis()); cv.restore()
            if (isShown) postInvalidateOnAnimation()
        }
    }

    private fun build(): LinearLayout {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        preview = Preview()
        root.addView(preview, LinearLayout.LayoutParams(Ui.MATCH, Ui.previewHeight(c, false)))
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(Ui.pill(c, "🚨 Wrong PIN") { jr.trigger("intruder", System.currentTimeMillis()) }, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(6) })
        for ((label, k) in listOf("🙂 It's you" to "owner", "🤨 Stranger" to "stranger", "🌑 No face" to "noface"))
            row.addView(Ui.pill(c, label) { val now = System.currentTimeMillis(); jr.trigger("check", now); preview.postDelayed({ jr.resolve(k, if (k == "owner") 94 else if (k == "stranger") 21 else 0) }, 2500) },
                LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { rightMargin = dp(6) })
        row.addView(Ui.previewToggle(c, preview))
        root.addView(android.widget.HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(row); setPadding(0, dp(6), 0, 0) })

        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        // ---- setup ----
        col.addView(Ui.section(c, "Setup"))
        val su = Ui.card(c)
        status = Ui.text(c, "", 14f, Ui.TEXT, true)
        su.addView(status)
        su.addView(Ui.text(c, "Do these once, in order. Photos stay only on this phone.", 12f, Ui.MUTED).apply { setPadding(0, dp(4), 0, dp(4)) })
        fun step(label: String, f: () -> Unit) = su.addView(Ui.button(c, label, false, f), LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        step("1. Allow camera") { a.requestPermissions(arrayOf(Manifest.permission.CAMERA), 21) }
        if (Build.VERSION.SDK_INT >= 33) step("2. Allow notifications") { a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 22) }
        step("3. Turn on wrong-PIN detection") {
            val comp = ComponentName(c, JarvisAdmin::class.java)
            a.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets Jarvis take a photo when someone enters a wrong PIN or pattern."))
        }
        step("4. Battery: no restrictions") { Ui.openBatterySettings(a) }
        su.addView(Ui.button(c, "5. Start Jarvis", true) { startService() }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        su.addView(Ui.button(c, "Set Jarvis as live wallpaper", true) { setLive() }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        su.addView(Ui.button(c, "🔕 Hide notification", false) { hideNotification() }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        su.addView(Ui.text(c, "Switch the notification off on the next screen. Jarvis keeps working.", 12f, Ui.MUTED).apply { setPadding(0, dp(4), 0, 0) })
        su.addView(Ui.button(c, "Stop Jarvis", false) { c.stopService(Intent(c, JarvisService::class.java)); refreshStatus() }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        su.addView(Ui.text(c, "After restarting your phone, open this app once and tap Start Jarvis again (Android requires it for the camera).", 12f, Ui.MUTED).apply { setPadding(0, dp(8), 0, 0) })
        col.addView(su)

        // ---- when ----
        col.addView(Ui.section(c, "When"))
        val wh = Ui.card(c)
        wh.addView(Ui.text(c, "Wrong PIN, pattern or password: always a photo, every attempt.", 13f, Ui.MUTED))
        Ui.toggle(c, wh, "Face check when the phone is opened", p.getBoolean("jv_face_on", false)) { on ->
            p.edit().putBoolean("jv_face_on", on).apply()
            if (on && FaceMatcher.owners(c).isEmpty()) a.toast("Add your face first (in \"Your face\")")
        }
        wh.addView(Ui.text(c, "On: every time the lock screen turns on he checks your face. You → \"Hi sir\" and the photo is deleted. Someone else → \"Who are you?\" and the photo is saved. Off: only wrong PIN photos.", 12f, Ui.MUTED))
        col.addView(wh)

        // ---- your face ----
        col.addView(Ui.section(c, "Your face"))
        val fc = Ui.card(c)
        faceCount = Ui.text(c, "", 14f, Ui.TEXT, true); fc.addView(faceCount)
        fc.addView(Ui.text(c, "Add 3–5 photos: straight, slightly left, slightly right, with and without glasses, in good light.", 12f, Ui.MUTED).apply { setPadding(0, dp(4), 0, 0) })
        fc.addView(Ui.button(c, "📸 Take a photo of me now", true) { enrollNow() }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(10) })
        fc.addView(Ui.button(c, "🖼 Choose a selfie", false) { a.pickSelfie() }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        fc.addView(Ui.button(c, "Remove my face", false) { confirm("Remove all saved face data?") { FaceMatcher.clearOwners(c); refreshFaces() } }, LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        fc.addView(Ui.text(c, "Face matching runs only on this phone. It's for fun, not security: it can be wrong in the dark or at odd angles.", 12f, Ui.MUTED).apply { setPadding(0, dp(8), 0, 0) })
        col.addView(fc)
        refreshFaces()

        // ---- look ----
        col.addView(Ui.section(c, "Look"))
        val lk = Ui.card(c)
        lk.addView(Ui.label(c, "Glow color"))
        lk.addView(Ui.choice(c, JarvisRenderer.ACCENT_NAMES, p.getInt("jv_accent", 0)) { p.edit().putInt("jv_accent", it).apply(); JarvisSettings.apply(p, jr) }.view)
        lk.addView(Ui.label(c, "Body color"))
        lk.addView(Ui.colors(c, p.getInt("jv_body", 0xFFE9ECF5.toInt())) { col2 -> p.edit().putInt("jv_body", if (col2 == 0xFF111318.toInt()) 0xFF2A2E3E.toInt() else col2).apply(); JarvisSettings.apply(p, jr) })
        for ((label, key, def) in listOf(Triple("When it's you", "jv_msg_owner", jr.msgOwner), Triple("When it's someone else", "jv_msg_stranger", jr.msgStranger), Triple("When he can't see a face", "jv_msg_noface", jr.msgNoFace))) {
            lk.addView(Ui.label(c, label)); lk.addView(input(def) { p.edit().putString(key, it).apply(); JarvisSettings.apply(p, jr) })
        }
        lk.addView(Ui.label(c, "What he says after a wrong PIN"))
        lk.addView(input(jr.msgIntruder) { p.edit().putString("jv_msg_intr", it).apply(); JarvisSettings.apply(p, jr) })
        col.addView(lk)

        // ---- log ----
        col.addView(Ui.section(c, "Jarvis Log"))
        val lg = Ui.card(c)
        logList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        lg.addView(logList)
        lg.addView(Ui.button(c, "Delete all photos", false) { confirm("Delete all photos? This can't be undone.") { JarvisCamera.logDir(c).listFiles()?.forEach { it.delete() }; refreshLog() } },
            LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = dp(8) })
        col.addView(lg)

        // ---- lock help ----
        col.addView(Ui.section(c, "Lock screen help"))
        col.addView(Ui.lockHelpCard(a))

        root.addView(Ui.tabify(c, col), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = dp(6) })
        refreshStatus(); refreshLog()
        p.registerOnSharedPreferenceChangeListener { _, key -> if (key == "jv_photo_time" || key == "jv_running") a.runOnUiThread { refreshLog(); refreshStatus() } }
        return root
    }

    private fun input(v: String, set: (String) -> Unit) = android.widget.EditText(c).apply {
        setText(v); setTextColor(Ui.TEXT); textSize = 15f; background = Ui.outlined(c, Ui.SURFACE, Ui.RULE, 12, 1f); setPadding(dp(12), dp(10), dp(12), dp(10))
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { set(s?.toString() ?: "") }
        })
    }

    private fun confirm(msg: String, yes: () -> Unit) {
        AlertDialog.Builder(a).setMessage(msg).setPositiveButton("Delete") { _, _ -> yes() }.setNegativeButton("Cancel", null).show()
    }

    fun refresh() { preview.postInvalidate(); refreshStatus(); refreshLog() }

    private fun refreshStatus() {
        val cam = c.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        val admin = dpm.isAdminActive(ComponentName(c, JarvisAdmin::class.java))
        val run = JarvisService.running()
        status.text = "Camera ${if (cam) "✓" else "✗"}   ·   Wrong-PIN ${if (admin) "✓" else "✗"}   ·   Jarvis ${if (run) "running ✓" else "stopped"}"
    }

    private fun startService() {
        if (c.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { a.toast("Allow the camera first (step 1)"); return }
        try {
            val i = Intent(c, JarvisService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            a.toast("Jarvis is running 👀")
        } catch (e: Exception) { a.toast("Couldn't start: ${e.message}") }
        view.postDelayed({ refreshStatus() }, 600)
    }

    private fun refreshFaces() {
        val n = FaceMatcher.owners(c).size
        faceCount.text = if (n == 0) "No face added yet" else "✓ $n photo${if (n > 1) "s" else ""} of you saved"
    }

    private fun addFace(bmp: android.graphics.Bitmap?) {
        val f = if (bmp == null) null else FaceMatcher.fingerprint(c, bmp)
        a.runOnUiThread {
            if (f == null) a.toast("No face found. Try again with more light, looking at the screen.")
            else { FaceMatcher.addOwner(c, f); refreshFaces(); a.toast("Face added ✓") }
        }
    }

    private fun enrollNow() {
        if (c.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { a.toast("Allow the camera first (Setup → step 1)"); return }
        a.toast("Look at the screen… 3, 2, 1")
        view.postDelayed({
            JarvisCamera.capture(c, "owner_setup") { file ->
                if (file == null) { a.toast("Camera didn't respond, try again"); return@capture }
                Thread { val bmp = try { ImageLoader.load(c, Uri.fromFile(file), 720, 960, 2_000_000L) } catch (e: Throwable) { null }; file.delete(); addFace(bmp) }.start()
            }
        }, 1800)
    }

    fun onSelfie(uri: Uri) {
        Thread { val bmp = try { ImageLoader.load(c, uri, 720, 960, 2_000_000L) } catch (e: Throwable) { null }; addFace(bmp) }.start()
    }

    private fun hideNotification() {
        try {
            if (Build.VERSION.SDK_INT >= 26) a.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, JarvisService.CH))
            else Ui.openBatterySettings(a)
            a.toast("Turn this notification off")
        } catch (e: Exception) {
            try { a.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)) } catch (e2: Exception) { }
        }
    }

    private fun setLive() {
        try {
            a.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(c, JarvisWallpaperService::class.java)))
            a.toast("Apply it to the home & lock screen")
        } catch (e: Exception) {
            try { a.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); a.toast("Choose \"MyWallpaper Jarvis\"") } catch (e2: Exception) { a.toast("This phone doesn't allow live wallpapers") }
        }
    }

    private fun refreshLog() {
        logList.removeAllViews()
        val files = JarvisCamera.logDir(c).listFiles()?.filter { it.extension == "jpg" && !it.name.contains("_check") && !it.name.contains("_owner") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) { logList.addView(Ui.text(c, "No photos yet. They'll appear here.", 13f, Ui.MUTED)); return }
        val fmt = SimpleDateFormat("EEE d MMM · h:mm a", Locale.getDefault())
        files.take(60).forEach { f ->
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
            val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = Ui.rounded(c, Ui.SURFACE2, 10); clipToOutline = true }
            Thread { val b = try { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 }) } catch (e: Throwable) { null }; a.runOnUiThread { img.setImageBitmap(b) } }.start()
            row.addView(img, LinearLayout.LayoutParams(dp(56), dp(72)))
            val intr = f.name.contains("intruder")
            val info = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(Ui.text(c, when { intr -> "🚨 Wrong PIN"; f.name.contains("stranger") -> "🤨 Not you"; f.name.contains("noface") -> "🌑 No face seen"; else -> "🔓 Lock screen" }, 14f, if (intr || f.name.contains("stranger")) 0xFFFF5A5F.toInt() else Ui.TEXT, true))
            info.addView(Ui.text(c, fmt.format(Date(f.name.substringBefore("_").toLongOrNull() ?: f.lastModified())), 12f, Ui.MUTED))
            row.addView(info, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            row.addView(Ui.pill(c, "🗑") { confirm("Delete this photo?") { f.delete(); refreshLog() } })
            row.setOnClickListener { show(f) }
            logList.addView(row)
        }
    }

    private fun show(f: File) {
        val iv = ImageView(c).apply { adjustViewBounds = true }
        Thread { val b = try { ImageLoader.load(c, Uri.fromFile(f), 1080, 1440, 3_000_000L) } catch (e: Throwable) { null }; a.runOnUiThread { iv.setImageBitmap(b) } }.start()
        AlertDialog.Builder(a).setView(iv).setPositiveButton("Close", null).show()
    }
}
