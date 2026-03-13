package com.example.weatherwidget

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches weather data from Open-Meteo (free, no API key required).
 * Uses synchronous OkHttp call — must be called from a background thread.
 *
 * API docs: https://open-meteo.com/en/docs
 */
object WeatherFetcher {

    private const val TAG = "WeatherFetcher"
    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetch current weather. Returns null on any error.
     * Must be called on a background thread.
     */
    fun fetch(lat: Double, lon: Double): WeatherData? {
        val url = buildUrl(lat, lon)
        Log.d(TAG, "Fetching weather: $url")

        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: run {
                Log.e(TAG, "Empty response body")
                return null
            }

            parse(body, lat, lon)
        } catch (e: Exception) {
            Log.e(TAG, "Fetch error: ${e.message}", e)
            null
        }
    }

    private fun buildUrl(lat: Double, lon: Double): String {
        // Format to 4 decimal places to avoid URL issues
        val latStr = "%.4f".format(lat)
        val lonStr = "%.4f".format(lon)
        return "$BASE_URL?latitude=$latStr&longitude=$lonStr" +
                "&current=temperature_2m,surface_pressure,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto" +
                "&forecast_days=1"
    }

    private fun parse(json: String, lat: Double, lon: Double): WeatherData? {
        return try {
            val root = JSONObject(json)

            // Current values
            val current = root.getJSONObject("current")
            val tempC = current.getDouble("temperature_2m").toFloat()
            val pressureHpa = current.getDouble("surface_pressure").toFloat()
            val wmoCode = current.getInt("weather_code")

            // Daily values (first day = today)
            val daily = root.getJSONObject("daily")
            val tempMax = daily.getJSONArray("temperature_2m_max").getDouble(0).toFloat()
            val tempMin = daily.getJSONArray("temperature_2m_min").getDouble(0).toFloat()
            val precipProb = daily.getJSONArray("precipitation_probability_max")
                .let { if (it.length() > 0) it.getDouble(0).toFloat() else 0f }

            WeatherData(
                temperatureC = tempC,
                temperatureMaxC = tempMax,
                temperatureMinC = tempMin,
                precipitationProbability = precipProb,
                surfacePressureHpa = pressureHpa,
                wmoCode = wmoCode,
                latitude = lat,
                longitude = lon
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
            null
        }
    }
}
