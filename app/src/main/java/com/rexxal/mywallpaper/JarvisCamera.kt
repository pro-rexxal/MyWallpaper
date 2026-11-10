package com.rexxal.mywallpaper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/** Takes one quiet photo with the front camera and saves it into the Jarvis Log. */
object JarvisCamera {
    fun logDir(c: Context) = File(c.filesDir, "jarvis").apply { mkdirs() }

    @SuppressLint("MissingPermission")
    fun capture(c: Context, kind: String, done: (File?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val thread = HandlerThread("jarvis-cam").apply { start() }
        val h = Handler(thread.looper)
        var finished = false
        var cam: CameraDevice? = null
        var reader: ImageReader? = null
        var tex: SurfaceTexture? = null
        fun finish(f: File?) {
            if (finished) return
            finished = true
            try { cam?.close() } catch (e: Exception) { }
            try { reader?.close() } catch (e: Exception) { }
            try { tex?.release() } catch (e: Exception) { }
            thread.quitSafely()
            main.post { done(f) }
        }
        h.postDelayed({ finish(null) }, 8000)   // give up if the camera never answers
        try {
            val cm = c.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
                ?: return finish(null)
            val ch = cm.getCameraCharacteristics(id)
            val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG) ?: return finish(null)
            val size = sizes.minByOrNull { abs(it.width * it.height - 900_000) } ?: sizes[0]
            val orient = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
            val rd = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2); reader = rd
            val st = SurfaceTexture(false).apply { setDefaultBufferSize(640, 480) }; tex = st
            val preview = Surface(st)
            rd.setOnImageAvailableListener({ r ->
                try {
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val buf = img.planes[0].buffer; val bytes = ByteArray(buf.remaining()); buf.get(bytes); img.close()
                    val f = File(logDir(c), "${System.currentTimeMillis()}_$kind.jpg")
                    FileOutputStream(f).use { it.write(bytes) }
                    finish(f)
                } catch (e: Exception) { finish(null) }
            }, h)
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    cam = d
                    try {
                        @Suppress("DEPRECATION")
                        d.createCaptureSession(listOf(preview, rd.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                try {
                                    // shoot the moment auto-exposure is ready (max ~280 ms), before anyone can react or cover the camera
                                    var shot = false
                                    fun shoot() {
                                        if (shot) return; shot = true
                                        try {
                                            val still = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                addTarget(rd.surface); set(CaptureRequest.JPEG_ORIENTATION, orient); set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                                            }
                                            s.capture(still.build(), null, h)
                                        } catch (e: Exception) { finish(null) }
                                    }
                                    val pr = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(preview); set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON) }
                                    var frames = 0
                                    s.setRepeatingRequest(pr.build(), object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(ss: CameraCaptureSession, rq: CaptureRequest, res: android.hardware.camera2.TotalCaptureResult) {
                                            frames++
                                            val ae = res.get(android.hardware.camera2.CaptureResult.CONTROL_AE_STATE)
                                            if (frames >= 2 && (ae == null || ae == android.hardware.camera2.CaptureResult.CONTROL_AE_STATE_CONVERGED || ae == android.hardware.camera2.CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED || ae == android.hardware.camera2.CaptureResult.CONTROL_AE_STATE_LOCKED)) shoot()
                                        }
                                    }, h)
                                    h.postDelayed({ shoot() }, 280)
                                } catch (e: Exception) { finish(null) }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) = finish(null)
                        }, h)
                    } catch (e: Exception) { finish(null) }
                }
                override fun onDisconnected(d: CameraDevice) = finish(null)
                override fun onError(d: CameraDevice, error: Int) = finish(null)
            }, h)
        } catch (e: Exception) { finish(null) }
    }
}
