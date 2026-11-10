package com.rexxal.mywallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.min

object ImageLoader {

    /** Loads a photo big enough for zooming, upright, without running out of memory. */
    fun load(c: Context, uri: Uri, screenW: Int, screenH: Int, maxPixels: Long = 12_000_000L): Bitmap {
        val cr = c.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw Exception("not an image")

        var sample = 1
        val tw = screenW * 3 / 2
        val th = screenH * 3 / 2
        while (bounds.outWidth / (sample * 2) >= tw && bounds.outHeight / (sample * 2) >= th) sample *= 2
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > maxPixels) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw Exception("can't read image")

        val rotation = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        return bmp
    }

    /** Biggest centered area of the image with the screen's shape. */
    fun centerCrop(bmp: Bitmap, aspect: Float): RectF {
        val w = min(bmp.width.toFloat(), bmp.height * aspect)
        val h = w / aspect
        return RectF((bmp.width - w) / 2f, (bmp.height - h) / 2f, (bmp.width + w) / 2f, (bmp.height + h) / 2f)
    }

    fun saveStyle(p: SharedPreferences, s: ImageProcessor.Settings) {
        p.edit()
            .putFloat("st_bright", s.brightness).putFloat("st_contrast", s.contrast)
            .putFloat("st_sat", s.saturation).putFloat("st_warm", s.warmth)
            .putInt("st_blur", s.blur).putFloat("st_dim", s.dim)
            .putFloat("st_vig", s.vignette).putString("st_filter", s.filter.name)
            .apply()
    }

    fun loadStyle(p: SharedPreferences) = ImageProcessor.Settings(
        brightness = p.getFloat("st_bright", 0f),
        contrast = p.getFloat("st_contrast", 1f),
        saturation = p.getFloat("st_sat", 1f),
        warmth = p.getFloat("st_warm", 0f),
        blur = p.getInt("st_blur", 0),
        dim = p.getFloat("st_dim", 0f),
        vignette = p.getFloat("st_vig", 0f),
        filter = try {
            ImageProcessor.Filter.valueOf(p.getString("st_filter", "NORMAL") ?: "NORMAL")
        } catch (e: Exception) {
            ImageProcessor.Filter.NORMAL
        }
    )
}
