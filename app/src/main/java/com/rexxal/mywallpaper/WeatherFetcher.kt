package com.rexxal.mywallpaper

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Gets the current weather for the user's city from Open-Meteo (free, no account). */
object WeatherFetcher {
    private const val MAX_AGE = 30 * 60_000L
    @Volatile private var running = false

    private fun get(url: String): JSONObject {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 8000; con.readTimeout = 8000
        try { return JSONObject(con.inputStream.bufferedReader().use { it.readText() }) } finally { con.disconnect() }
    }

    fun codeToWeather(code: Int): String = when (code) {
        0, 1 -> "Clear"
        2, 3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Storm"
        else -> "Cloudy"
    }

    /** Refreshes in the background when the saved weather is old. [done] gets a short message, on a background thread. */
    fun refresh(c: Context, force: Boolean = false, done: ((String) -> Unit)? = null) {
        val p = Ui.prefs(c)
        val city = (p.getString("lv_city", "") ?: "").trim()
        if (city.isEmpty()) { done?.invoke("Type your city first"); return }
        if (!force && System.currentTimeMillis() - p.getLong("wx_time", 0L) < MAX_AGE && p.getString("wx_for", "") == city) return
        if (running) return
        running = true
        Thread {
            try {
                val geo = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"))
                val r = geo.optJSONArray("results")?.optJSONObject(0) ?: throw Exception("City not found")
                val lat = r.getDouble("latitude"); val lon = r.getDouble("longitude")
                val wx = get("https://api.open-meteo.com/v1/forecast?current=weather_code&latitude=$lat&longitude=$lon")
                val code = wx.getJSONObject("current").getInt("weather_code")
                val w = codeToWeather(code)
                p.edit().putString("wx_now", w).putLong("wx_time", System.currentTimeMillis()).putString("wx_for", city)
                    .putString("wx_place", r.optString("name", city)).apply()
                done?.invoke("${r.optString("name", city)}: $w")
            } catch (e: Exception) {
                done?.invoke(if (e.message == "City not found") "City not found" else "No internet right now")
            } finally {
                running = false
            }
        }.start()
    }
}
