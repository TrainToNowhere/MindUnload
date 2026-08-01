package com.app.mindunload.ai

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Fetches a compact daily weather forecast from Open-Meteo (free, no API key)
 * for the morning briefing. Returns null on missing configuration or errors —
 * the briefing then simply comes without weather.
 */
object WeatherService {

    suspend fun fetchTodaySummary(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
                            "uv_index_max,weather_code,wind_speed_10m_max&timezone=auto&forecast_days=1",
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val body = try {
                    connection.inputStream.bufferedReader().readText()
                } finally {
                    connection.disconnect()
                }
                val daily = JSONObject(body).getJSONObject("daily")
                fun value(key: String): Double? =
                    daily.optJSONArray(key)?.optDouble(0)?.takeIf { !it.isNaN() }

                val tMin = value("temperature_2m_min")
                val tMax = value("temperature_2m_max")
                val rain = value("precipitation_probability_max")
                val uv = value("uv_index_max")
                val wind = value("wind_speed_10m_max")
                val code = value("weather_code")?.toInt()

                buildString {
                    code?.let { append("${describeWeatherCode(it)}, ") }
                    if (tMin != null && tMax != null) {
                        append("${tMin.toInt()} to ${tMax.toInt()} °C")
                    }
                    rain?.let { append(", precipitation probability ${it.toInt()} %") }
                    uv?.let { append(", UV index ${"%.1f".format(it)}") }
                    wind?.let { append(", wind up to ${it.toInt()} km/h") }
                }.trim().trim(',').ifBlank { null }
            }.onFailure { Log.w("Weather", "fetch failed: ${it.message}") }.getOrNull()
        }

    /** WMO weather code → terse description, model-facing only (coarse groups suffice for the briefing prompt). */
    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "clear"
        1, 2 -> "mostly sunny"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..65 -> "rain"
        66, 67 -> "freezing rain"
        in 71..77 -> "snowfall"
        in 80..82 -> "rain showers"
        85, 86 -> "snow showers"
        in 95..99 -> "thunderstorms"
        else -> "changeable"
    }
}
