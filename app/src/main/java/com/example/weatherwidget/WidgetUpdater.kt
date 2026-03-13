package com.example.weatherwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Handles all RemoteViews construction and widget updates.
 * Separated from the provider to keep the BroadcastReceiver lean.
 *
 * KEY RULES for RemoteViews (to avoid "Can't load widget"):
 * 1. Never call RemoteViews methods that don't exist (setViewPadding is API 26+, we target 26+, OK)
 * 2. Always catch exceptions around RemoteViews construction
 * 3. Always use applicationContext when calling AppWidgetManager.updateAppWidget
 * 4. Bitmap for background must be recycled carefully - set it then null the ref
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"

    fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            val views = buildRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget $appWidgetId: ${e.message}", e)
            // Update with a safe fallback to prevent "Can't load widget"
            try {
                val fallback = buildFallbackViews(context)
                appWidgetManager.updateAppWidget(appWidgetId, fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback update also failed: ${e2.message}", e2)
            }
        }
    }

    private fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val ctx = context.applicationContext
        val views = RemoteViews(ctx.packageName, R.layout.widget_layout)

        // --- Background ---
        setBackground(ctx, views)

        // --- Clickable: temperature toggles C/F ---
        val toggleUnitsIntent = Intent(ctx, WeatherWidgetProvider::class.java).apply {
            action = WeatherWidgetProvider.ACTION_TOGGLE_UNITS
        }
        val toggleUnitsPi = PendingIntent.getBroadcast(
            ctx, appWidgetId + 1000,
            toggleUnitsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tv_temperature, toggleUnitsPi)

        // --- Clickable: location toggle icon ---
        val toggleLocIntent = Intent(ctx, WeatherWidgetProvider::class.java).apply {
            action = WeatherWidgetProvider.ACTION_TOGGLE_LOCATION
        }
        val toggleLocPi = PendingIntent.getBroadcast(
            ctx, appWidgetId + 2000,
            toggleLocIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_location_toggle, toggleLocPi)

        // --- Location icon ---
        val useHome = Prefs.isUseHome(ctx)
        views.setImageViewResource(
            R.id.btn_location_toggle,
            if (useHome) R.drawable.ic_location_home else R.drawable.ic_location_gps
        )

        // --- Weather data ---
        val useCelsius = Prefs.isUseCelsius(ctx)
        if (Prefs.hasCachedWeather(ctx)) {
            setWeatherViews(ctx, views, useCelsius)
        } else {
            views.setTextViewText(R.id.tv_temperature, "…")
            views.setTextViewText(R.id.tv_weather_desc, ctx.getString(R.string.loading))
            views.setTextViewText(R.id.tv_hi_lo, "")
            views.setTextViewText(R.id.tv_precip, "")
            views.setTextViewText(R.id.tv_pressure, "")
        }

        // --- Sun data ---
        setSunViews(ctx, views)

        // --- Last updated ---
        val lastUpdate = Prefs.getLastUpdateTime(ctx)
        if (lastUpdate > 0) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            views.setTextViewText(R.id.tv_last_updated, fmt.format(lastUpdate))
        }

        return views
    }

    private fun setBackground(ctx: Context, views: RemoteViews) {
        try {
            val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60.0 + now.get(Calendar.MINUTE)

            val cachedLat = Prefs.getCachedLat(ctx)
            val cachedLon = Prefs.getCachedLon(ctx)

            // Default to reasonable sun times if no location cached yet
            val sunTimes = if (cachedLat != Double.MIN_VALUE) {
                SunCalculator.calculate(
                    cachedLat, cachedLon,
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH)
                )
            } else null

            val sunrise = sunTimes?.sunriseMinutesUtc ?: (6 * 60.0)
            val sunset = sunTimes?.sunsetMinutesUtc ?: (20 * 60.0)

            val (topColor, bottomColor) = SkyBackground.getGradientColors(nowMinutes, sunrise, sunset)

            val bmp = createGradientBitmap(topColor, bottomColor)
            views.setImageViewBitmap(R.id.background_view, bmp)
        } catch (e: Exception) {
            Log.e(TAG, "Background error: ${e.message}", e)
        }
    }

    private fun createGradientBitmap(topColor: Int, bottomColor: Int): Bitmap {
        val width = 400
        val height = 200
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            topColor, bottomColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 16f, 16f, paint)
        return bmp
    }

    private fun setWeatherViews(ctx: Context, views: RemoteViews, useCelsius: Boolean) {
        val tempC = Prefs.getCachedTempC(ctx)
        val maxC = Prefs.getCachedTempMaxC(ctx)
        val minC = Prefs.getCachedTempMinC(ctx)
        val precip = Prefs.getCachedPrecip(ctx)
        val pressure = Prefs.getCachedPressure(ctx)
        val wmo = Prefs.getCachedWmo(ctx)

        // Temperature display
        val (tempDisplay, maxDisplay, minDisplay) = if (useCelsius) {
            Triple(
                "${tempC.roundToInt()}°C",
                "${maxC.roundToInt()}°",
                "${minC.roundToInt()}°"
            )
        } else {
            Triple(
                "${cToF(tempC).roundToInt()}°F",
                "${cToF(maxC).roundToInt()}°",
                "${cToF(minC).roundToInt()}°"
            )
        }

        views.setTextViewText(R.id.tv_temperature, tempDisplay)
        views.setTextViewText(R.id.tv_hi_lo, "H:$maxDisplay  L:$minDisplay")
        views.setTextViewText(R.id.tv_precip, "${precip.roundToInt()}%")

        // Pressure with trend arrow
        val prevPressure = Prefs.getPrevPressure(ctx)
        val pressureArrow = when {
            prevPressure < 0 -> R.drawable.ic_arrow_right
            pressure > prevPressure + 0.5f -> R.drawable.ic_arrow_up
            pressure < prevPressure - 0.5f -> R.drawable.ic_arrow_down
            else -> R.drawable.ic_arrow_right
        }
        views.setImageViewResource(R.id.iv_pressure_arrow, pressureArrow)
        views.setTextViewText(R.id.tv_pressure, "${"%.0f".format(pressure)} hPa")

        // Weather icon and description
        val info = WmoWeather.getInfo(wmo)
        views.setImageViewResource(R.id.iv_weather_icon, info.iconRes)
        views.setTextViewText(R.id.tv_weather_desc, info.description)
    }

    private fun setSunViews(ctx: Context, views: RemoteViews) {
        val cachedLat = Prefs.getCachedLat(ctx)
        val cachedLon = Prefs.getCachedLon(ctx)

        if (cachedLat == Double.MIN_VALUE) {
            views.setTextViewText(R.id.tv_sunrise, "--:--")
            views.setTextViewText(R.id.tv_sunset, "--:--")
            views.setTextViewText(R.id.tv_daylight, "")
            views.setTextViewText(R.id.tv_solar_noon, "")
            return
        }

        val now = Calendar.getInstance()
        val sunTimes = SunCalculator.calculate(
            cachedLat, cachedLon,
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )

        if (sunTimes == null) {
            views.setTextViewText(R.id.tv_sunrise, "Polar")
            views.setTextViewText(R.id.tv_sunset, "Polar")
            views.setTextViewText(R.id.tv_daylight, "")
            views.setTextViewText(R.id.tv_solar_noon, "")
            return
        }

        // Convert UTC minutes to local time
        val tzOffsetMin = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000.0
        val sunriseLocal = sunTimes.sunriseMinutesUtc + tzOffsetMin
        val sunsetLocal = sunTimes.sunsetMinutesUtc + tzOffsetMin
        val noonLocal = sunTimes.solarNoonMinutesUtc + tzOffsetMin

        views.setTextViewText(R.id.tv_sunrise, formatMinutes(sunriseLocal))
        views.setTextViewText(R.id.tv_sunset, formatMinutes(sunsetLocal))

        val daylightMin = sunTimes.daylightMinutes.toInt()
        val hours = daylightMin / 60
        val mins = daylightMin % 60
        views.setTextViewText(R.id.tv_daylight, "${hours}h ${mins}m")
        views.setTextViewText(R.id.tv_solar_noon, "Noon: ${formatMinutes(noonLocal)}")
    }

    private fun formatMinutes(minutesFromMidnight: Double): String {
        val totalMin = ((minutesFromMidnight % 1440) + 1440).toInt() % 1440
        val h = totalMin / 60
        val m = totalMin % 60
        return "%d:%02d".format(h, m)
    }

    private fun cToF(c: Float) = c * 9f / 5f + 32f

    private fun buildFallbackViews(context: Context): RemoteViews {
        val ctx = context.applicationContext
        val views = RemoteViews(ctx.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.tv_temperature, "--")
        views.setTextViewText(R.id.tv_weather_desc, "Tap to refresh")
        return views
    }
}
