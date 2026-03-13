package com.example.weatherwidget

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized SharedPreferences helper.
 * Using application context to avoid memory leaks from widget/service contexts.
 */
object Prefs {
    private const val PREF_NAME = "weather_widget_prefs"

    // Keys
    const val KEY_USE_HOME = "use_home_location"
    const val KEY_HOME_LAT = "home_latitude"
    const val KEY_HOME_LON = "home_longitude"
    const val KEY_USE_CELSIUS = "use_celsius"
    const val KEY_LAST_PRESSURE = "last_pressure"
    const val KEY_PREV_PRESSURE = "prev_pressure"
    const val KEY_LAST_UPDATE = "last_update_time"
    const val KEY_CACHED_TEMP_C = "cached_temp_c"
    const val KEY_CACHED_TEMP_MAX_C = "cached_temp_max_c"
    const val KEY_CACHED_TEMP_MIN_C = "cached_temp_min_c"
    const val KEY_CACHED_PRECIP = "cached_precip_pct"
    const val KEY_CACHED_PRESSURE = "cached_pressure_hpa"
    const val KEY_CACHED_WMO = "cached_wmo_code"
    const val KEY_CACHED_LAT = "cached_lat"
    const val KEY_CACHED_LON = "cached_lon"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isUseHome(context: Context) = prefs(context).getBoolean(KEY_USE_HOME, false)
    fun setUseHome(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_USE_HOME, v).apply()

    fun hasHomeLocation(context: Context): Boolean {
        val p = prefs(context)
        return p.contains(KEY_HOME_LAT) && p.contains(KEY_HOME_LON)
    }

    fun getHomeLat(context: Context) = prefs(context).getFloat(KEY_HOME_LAT, 0f).toDouble()
    fun getHomeLon(context: Context) = prefs(context).getFloat(KEY_HOME_LON, 0f).toDouble()

    fun setHomeLocation(context: Context, lat: Double, lon: Double) {
        prefs(context).edit()
            .putFloat(KEY_HOME_LAT, lat.toFloat())
            .putFloat(KEY_HOME_LON, lon.toFloat())
            .apply()
    }

    fun isUseCelsius(context: Context) = prefs(context).getBoolean(KEY_USE_CELSIUS, true)
    fun setUseCelsius(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_USE_CELSIUS, v).apply()

    fun getLastPressure(context: Context) = prefs(context).getFloat(KEY_LAST_PRESSURE, -1f)
    fun getPrevPressure(context: Context) = prefs(context).getFloat(KEY_PREV_PRESSURE, -1f)
    fun setLastPressure(context: Context, current: Float, previous: Float) {
        prefs(context).edit()
            .putFloat(KEY_LAST_PRESSURE, current)
            .putFloat(KEY_PREV_PRESSURE, previous)
            .apply()
    }

    fun saveWeatherCache(
        context: Context,
        tempC: Float,
        tempMaxC: Float,
        tempMinC: Float,
        precipPct: Float,
        pressureHpa: Float,
        wmoCode: Int,
        lat: Double,
        lon: Double
    ) {
        prefs(context).edit()
            .putFloat(KEY_CACHED_TEMP_C, tempC)
            .putFloat(KEY_CACHED_TEMP_MAX_C, tempMaxC)
            .putFloat(KEY_CACHED_TEMP_MIN_C, tempMinC)
            .putFloat(KEY_CACHED_PRECIP, precipPct)
            .putFloat(KEY_CACHED_PRESSURE, pressureHpa)
            .putInt(KEY_CACHED_WMO, wmoCode)
            .putFloat(KEY_CACHED_LAT, lat.toFloat())
            .putFloat(KEY_CACHED_LON, lon.toFloat())
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    fun getCachedTempC(context: Context) = prefs(context).getFloat(KEY_CACHED_TEMP_C, Float.MIN_VALUE)
    fun getCachedTempMaxC(context: Context) = prefs(context).getFloat(KEY_CACHED_TEMP_MAX_C, Float.MIN_VALUE)
    fun getCachedTempMinC(context: Context) = prefs(context).getFloat(KEY_CACHED_TEMP_MIN_C, Float.MIN_VALUE)
    fun getCachedPrecip(context: Context) = prefs(context).getFloat(KEY_CACHED_PRECIP, -1f)
    fun getCachedPressure(context: Context) = prefs(context).getFloat(KEY_CACHED_PRESSURE, -1f)
    fun getCachedWmo(context: Context) = prefs(context).getInt(KEY_CACHED_WMO, -1)
    fun getCachedLat(context: Context) = prefs(context).getFloat(KEY_CACHED_LAT, Float.MIN_VALUE).toDouble()
    fun getCachedLon(context: Context) = prefs(context).getFloat(KEY_CACHED_LON, Float.MIN_VALUE).toDouble()
    fun getLastUpdateTime(context: Context) = prefs(context).getLong(KEY_LAST_UPDATE, 0L)

    fun hasCachedWeather(context: Context) = getCachedTempC(context) != Float.MIN_VALUE
}
