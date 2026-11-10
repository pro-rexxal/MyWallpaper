package com.rexxal.mywallpaper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** Jarvis's voice (phone's text-to-speech, offline) and small synthesized sound effects. All off unless switched on. */
class JarvisSound(private val ctx: Context) {
    private val prefs = Ui.prefs(ctx)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val rate = 22050
    private val cache = HashMap<String, ShortArray>()

    private val on get() = prefs.getBoolean("jv_snd", false)
    private val vol get() = prefs.getInt("jv_vol", 70) / 100f
    private val alarmOnly get() = prefs.getBoolean("jv_alarm_only", false)

    /** Phone on silent or vibrate: stay quiet. */
    private fun silent(): Boolean = try { ctx.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL } catch (e: Exception) { false }

    fun release() { try { tts?.shutdown() } catch (e: Exception) { }; tts = null; ttsReady = false }

    fun sfx(name: String) {
        if (!on || !prefs.getBoolean("jv_fx", true) || silent()) return
        if (alarmOnly && name != "alarm") return
        val pcm = cache.getOrPut(name) { make(name) }
        if (pcm.isEmpty()) return
        Thread {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                track.write(pcm, 0, pcm.size); track.setVolume(vol); track.play()
                Thread.sleep(pcm.size * 1000L / rate + 80); track.release()
            } catch (e: Exception) { }
        }.start()
    }

    fun say(text: String) {
        if (!on || !prefs.getBoolean("jv_voice", true) || silent()) return
        if (alarmOnly && !Regex("Gotcha|Intruder|Who are you", RegexOption.IGNORE_CASE).containsMatchIn(text)) return
        val clean = text.replace(Regex("[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}\\x{FE0F}…]"), "").replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return
        val speak = {
            tts?.let { t ->
                val robot = prefs.getBoolean("jv_robot", true)
                t.setPitch(if (robot) .6f else 1f); t.setSpeechRate(if (robot) 1.1f else 1f)
                val b = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol) }
                t.speak(clean, TextToSpeech.QUEUE_FLUSH, b, "jv")
            }
        }
        if (ttsReady) speak() else if (tts == null) {
            tts = TextToSpeech(ctx.applicationContext) { st -> if (st == TextToSpeech.SUCCESS) { ttsReady = true; try { tts?.language = Locale.getDefault() } catch (e: Exception) { }; speak() } }
        }
    }

    // ----- tiny synth -----
    private fun make(name: String): ShortArray {
        val buf = FloatArray((rate * 1.4f).toInt())
        var len = 0
        fun tone(f: Float, d: Float, wave: Int, v: Float, at: Float = 0f, f2: Float = f) {
            val s0 = (at * rate).toInt(); val n = (d * rate).toInt(); var ph = 0.0
            for (i in 0 until n) { val k = i.toFloat() / n; val fr = f * Math.pow((f2 / f).toDouble(), k.toDouble()); ph += 2 * PI * fr / rate
                val x = when (wave) { 0 -> sin(ph).toFloat(); 1 -> if (sin(ph) >= 0) 1f else -1f; else -> (2 * ((ph / (2 * PI)) % 1.0) - 1).toFloat() }
                val env = (if (i < rate / 100) i / (rate / 100f) else 1f) * Math.pow(.001, k.toDouble()).toFloat()
                val j = s0 + i; if (j < buf.size) { buf[j] += x * v * env; if (j > len) len = j } }
        }
        fun noise(d: Float, v: Float, hp: Float, at: Float = 0f) {
            val s0 = (at * rate).toInt(); val n = (d * rate).toInt(); val a = (1f / (1f + 2f * PI.toFloat() * hp / rate)); var prevIn = 0f; var prevOut = 0f
            for (i in 0 until n) { val x = (Math.random() * 2 - 1).toFloat(); val y = a * (prevOut + x - prevIn); prevIn = x; prevOut = y
                val j = s0 + i; if (j < buf.size) { buf[j] += y * v * (1 - i.toFloat() / n); if (j > len) len = j } }
        }
        when (name) {
            "shutter" -> { noise(.05f, .9f, 2500f); noise(.07f, .7f, 1500f, .07f) }
            "key" -> noise(.025f, .35f, 3000f)
            "alarm" -> for (k in 0..2) { tone(880f, .18f, 2, .3f, k * .4f); tone(620f, .18f, 2, .3f, k * .4f + .2f) }
            "chirp" -> { tone(700f, .12f, 0, .5f, 0f, 1200f); tone(1000f, .14f, 0, .5f, .13f, 1600f) }
            "beep" -> { tone(1300f, .07f, 1, .2f); tone(1700f, .05f, 1, .18f, .08f) }
            "grumble" -> tone(220f, .25f, 2, .3f, 0f, 140f)
            "hmm" -> tone(500f, .18f, 0, .35f, 0f, 420f)
            "jet", "land" -> noise(.6f, .3f, 600f)
            "fix" -> { noise(.025f, .35f, 3000f); noise(.025f, .35f, 3000f, .09f) }
        }
        return ShortArray(len + 1) { (buf[it].coerceIn(-1f, 1f) * 30000).toInt().toShort() }
    }
}
