package com.rexxal.mywallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device face check: finds the face with ML Kit, turns it into a fingerprint with a small FaceNet model,
 * and compares it with the owner's saved fingerprints. Nothing leaves the phone.
 */
object FaceMatcher {
    private const val KEY = "jv_owner_faces"
    private const val SIZE = 160
    private var interp: Interpreter? = null
    private var triedModel = false

    sealed class Result { object NoFace : Result(); class Done(val owner: Boolean, val score: Int) : Result() }

    private val detector by lazy {
        FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f).build())
    }

    private fun model(c: Context): Interpreter? {
        if (triedModel) return interp
        triedModel = true
        interp = try {
            val fd = c.assets.openFd("facenet.tflite")
            val buf = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            Interpreter(buf, Interpreter.Options().setNumThreads(2))
        } catch (e: Throwable) { null }
        return interp
    }

    fun usingModel(c: Context) = model(c) != null

    /** Fingerprint of the biggest face in [bmp], or null when there's no face. Call off the main thread. */
    fun fingerprint(c: Context, bmp: Bitmap): FloatArray? {
        val faces = try { Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0))) } catch (e: Exception) { return null }
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        val m = model(c)
        if (m == null) {
            // fallback: proportions between eyes, nose, mouth and cheeks
            fun lm(t: Int) = face.getLandmark(t)?.position
            val le = lm(FaceLandmark.LEFT_EYE) ?: return null; val re = lm(FaceLandmark.RIGHT_EYE) ?: return null
            val pts = listOf(FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM, FaceLandmark.LEFT_CHEEK, FaceLandmark.RIGHT_CHEEK).mapNotNull { lm(it) }
            if (pts.size < 6) return null
            val eye = hypot(le.x - re.x, le.y - re.y).coerceAtLeast(1f)
            val all = listOf(le, re) + pts
            val v = ArrayList<Float>()
            for (i in all.indices) for (j in i + 1 until all.size) v += hypot(all[i].x - all[j].x, all[i].y - all[j].y) / eye
            return normalize(v.toFloatArray())
        }
        // crop a square around the face, a little bigger than the box, upright
        val b: Rect = face.boundingBox
        val side = (max(b.width(), b.height()) * 1.2f).toInt()
        val cx = b.centerX(); val cy = b.centerY()
        val x0 = (cx - side / 2).coerceIn(0, bmp.width - 1); val y0 = (cy - side / 2).coerceIn(0, bmp.height - 1)
        val w = min(side, bmp.width - x0); val h = min(side, bmp.height - y0)
        if (w < 20 || h < 20) return null
        val rot = Matrix().apply { postRotate(-face.headEulerAngleZ, w / 2f, h / 2f) }
        val crop = Bitmap.createBitmap(bmp, x0, y0, w, h, rot, true)
        val face160 = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE); face160.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        // per-image standardization
        var mean = 0.0; for (p in px) mean += ((p shr 16 and 255) + (p shr 8 and 255) + (p and 255)) / 3.0; mean /= px.size
        var vv = 0.0; for (p in px) { for (ch in intArrayOf(p shr 16 and 255, p shr 8 and 255, p and 255)) { val d = ch - mean; vv += d * d } }
        val std = max(sqrt(vv / (px.size * 3)), 1.0 / sqrt(px.size * 3.0))
        val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        for (p in px) { input.putFloat((((p shr 16 and 255) - mean) / std).toFloat()); input.putFloat((((p shr 8 and 255) - mean) / std).toFloat()); input.putFloat((((p and 255) - mean) / std).toFloat()) }
        input.rewind()
        val outSize = m.getOutputTensor(0).shape().last()
        val out = Array(1) { FloatArray(outSize) }
        m.run(input, out)
        return normalize(out[0])
    }

    private fun normalize(v: FloatArray): FloatArray { var n = 0f; for (x in v) n += x * x; n = sqrt(n).coerceAtLeast(1e-6f); return FloatArray(v.size) { v[it] / n } }
    private fun cosine(a: FloatArray, b: FloatArray): Float { if (a.size != b.size) return 0f; var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

    fun owners(c: Context): List<FloatArray> {
        val s = Ui.prefs(c).getString(KEY, null) ?: return emptyList()
        return try { val arr = JSONArray(s); List(arr.length()) { i -> val a = arr.getJSONArray(i); FloatArray(a.length()) { a.getDouble(it).toFloat() } } } catch (e: Exception) { emptyList() }
    }

    fun addOwner(c: Context, f: FloatArray) {
        val arr = try { JSONArray(Ui.prefs(c).getString(KEY, "[]")) } catch (e: Exception) { JSONArray() }
        arr.put(JSONArray(f.map { it.toDouble() }))
        Ui.prefs(c).edit().putString(KEY, arr.toString()).apply()
    }

    fun clearOwners(c: Context) = Ui.prefs(c).edit().remove(KEY).apply()

    /** Compares a photo with the owner. Call off the main thread. */
    fun check(c: Context, bmp: Bitmap): Result {
        val f = fingerprint(c, bmp) ?: return Result.NoFace
        val owners = owners(c)
        if (owners.isEmpty()) return Result.Done(false, 0)
        val best = owners.maxOf { cosine(f, it) }
        val threshold = if (usingModel(c)) 0.60f else 0.985f
        val score = if (usingModel(c)) ((best + 1) / 2 * 100).toInt() else (best * 100).toInt()
        return Result.Done(best >= threshold, score.coerceIn(0, 99))
    }
}
