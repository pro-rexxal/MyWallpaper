package com.rexxal.mywallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** Changes the wallpaper from a chosen folder on a timer. */
object AutoChanger {

    const val ACTION_CHANGE = "com.rexxal.mywallpaper.AUTO_CHANGE"
    val INTERVALS_MIN = intArrayOf(15, 30, 60, 180, 360, 720, 1440)
    val INTERVAL_LABELS = listOf("15 min", "30 min", "1 hour", "3 hours", "6 hours", "12 hours", "Daily")
    val TARGET_FLAGS = intArrayOf(
        WallpaperManager.FLAG_SYSTEM,
        WallpaperManager.FLAG_LOCK,
        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    )

    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 0,
        Intent(c, AutoReceiver::class.java).setAction(ACTION_CHANGE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun schedule(c: Context) {
        val p = Ui.prefs(c)
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c)
        am.cancel(pi)
        if (!p.getBoolean("auto_on", false) || p.getString("auto_folder", null) == null) return
        val idx = p.getInt("auto_interval", 2).coerceIn(0, INTERVALS_MIN.size - 1)
        val ms = INTERVALS_MIN[idx] * 60_000L
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + ms, ms, pi)
    }

    /** Lists image documents in the chosen folder, sorted by name. */
    fun listImages(c: Context): List<Uri> {
        val tree = Ui.prefs(c).getString("auto_folder", null) ?: return emptyList()
        val treeUri = Uri.parse(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val found = mutableListOf<Pair<String, Uri>>()
        c.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getString(0) ?: continue
                val name = cur.getString(1) ?: ""
                val mime = cur.getString(2) ?: ""
                if (mime.startsWith("image/")) {
                    found += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
        }
        return found.sortedBy { it.first.lowercase() }.map { it.second }
    }

    fun folderName(c: Context): String? {
        val tree = Ui.prefs(c).getString("auto_folder", null) ?: return null
        return try {
            val treeUri = Uri.parse(tree)
            val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            c.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    /** Sets the next photo. Returns a short message about what happened. Call off the main thread. */
    fun changeNow(c: Context): String {
        val p = Ui.prefs(c)
        val images = try { listImages(c) } catch (e: Exception) { emptyList() }
        if (images.isEmpty()) return "No photos found in the folder"

        val index = if (p.getBoolean("auto_shuffle", true)) {
            val last = p.getInt("auto_index", -1)
            var r = (images.indices).random()
            if (images.size > 1 && r == last) r = (r + 1) % images.size
            r
        } else {
            (p.getInt("auto_index", -1) + 1) % images.size
        }
        p.edit().putInt("auto_index", index).apply()

        val (sw, sh) = Ui.screenSize(c)
        val bmp = ImageLoader.load(c, images[index], sw, sh, maxPixels = 8_000_000L)
        val crop = ImageLoader.centerCrop(bmp, sw.toFloat() / sh)
        val style = if (p.getBoolean("auto_style", false)) ImageLoader.loadStyle(p) else ImageProcessor.Settings()
        val out = ImageProcessor.render(bmp, crop, sw, sh, style, hq = true)
        bmp.recycle()
        val target = p.getInt("auto_target", 2).coerceIn(0, 2)
        WallpaperManager.getInstance(c).setBitmap(out, null, true, TARGET_FLAGS[target])
        out.recycle()
        return "Changed (${index + 1} of ${images.size})"
    }
}

class AutoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            AutoChanger.ACTION_CHANGE -> {
                val pr = goAsync()
                Thread {
                    try {
                        AutoChanger.changeNow(c)
                    } catch (e: Throwable) {
                        // try again next time
                    } finally {
                        pr.finish()
                    }
                }.start()
            }
            else -> AutoChanger.schedule(c) // phone restarted or app updated
        }
    }
}
