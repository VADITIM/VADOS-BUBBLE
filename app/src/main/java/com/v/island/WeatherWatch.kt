package com.v.island

import android.content.Context
import android.location.Location
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// The panel's weather line is read, not pushed: nothing on this phone tells us the sky changed,
// so a request is answered from cache within CACHE_MILLIS and fetched fresh otherwise.
object WeatherWatch {

    private const val CACHE_MILLIS = 15 * 60 * 1000L

    private val worker = Executors.newSingleThreadExecutor()
    private var cached: JSONObject? = null
    private var cachedAt = 0L

    fun request(context: Context, onResult: (JSONObject?) -> Unit) {
        val now = System.currentTimeMillis()
        val hit = cached
        if (hit != null && now - cachedAt < CACHE_MILLIS) {
            onResult(hit)
            return
        }
        worker.execute {
            val result = runCatching { fetch(context) }.getOrNull()
            if (result != null) {
                cached = result
                cachedAt = now
            }
            onResult(result ?: cached)
        }
    }

    private fun fetch(context: Context): JSONObject? {
        val location = lastKnownLocation(context) ?: return null
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                "&longitude=${location.longitude}&current=temperature_2m,weather_code"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 6000
        return connection.inputStream.use { stream ->
            val current = JSONObject(stream.bufferedReader().readText()).getJSONObject("current")
            JSONObject()
                .put("celsius", current.getDouble("temperature_2m"))
                .put("sky", skyFor(current.getInt("weather_code")))
        }
    }

    // Read off the phone's own network location fix, which needs no dialog of its own once
    // ACCESS_COARSE_LOCATION is granted the way READ_PHONE_STATE is — see grant.ps1.
    private fun lastKnownLocation(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    // Open-Meteo's WMO weather codes, collapsed to the three skies pill.html can draw.
    private fun skyFor(code: Int): String = when {
        code == 0 || code == 1 -> "clear"
        code in 51..67 || code in 80..99 -> "rain"
        else -> "cloud"
    }
}
