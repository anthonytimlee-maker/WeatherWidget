package com.example.weatherwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that fetches weather data and updates all widget instances.
 *
 * Using CoroutineWorker for clean coroutine-based async execution.
 * WorkManager handles scheduling, retries, and battery optimization automatically.
 *
 * IMPORTANT: WorkManager workers are NOT declared in AndroidManifest.xml.
 * WorkManager discovers them via reflection using the class name.
 */
class WeatherUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WeatherUpdateWorker"
        const val WORK_NAME = "weather_update_periodic"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting weather update work")

        return withContext(Dispatchers.IO) {
            try {
                val ctx = context.applicationContext

                // Determine which location to use
                val location: LocationHelper.LatLon? = if (Prefs.isUseHome(ctx) && Prefs.hasHomeLocation(ctx)) {
                    LocationHelper.LatLon(Prefs.getHomeLat(ctx), Prefs.getHomeLon(ctx))
                } else {
                    LocationHelper.getLocation(ctx)
                }

                if (location == null) {
                    Log.w(TAG, "No location available, using cached data if any")
                    // Still update widget UI (will show cached data)
                    updateWidgets(ctx)
                    return@withContext Result.retry()
                }

                // Fetch weather
                val weather = WeatherFetcher.fetch(location.lat, location.lon)

                if (weather == null) {
                    Log.w(TAG, "Weather fetch returned null, keeping cached data")
                    updateWidgets(ctx)
                    return@withContext Result.retry()
                }

                // Save pressure history for trend arrow
                val lastPressure = Prefs.getLastPressure(ctx)
                Prefs.setLastPressure(ctx, weather.surfacePressureHpa, lastPressure)

                // Cache the weather data
                Prefs.saveWeatherCache(
                    ctx,
                    weather.temperatureC,
                    weather.temperatureMaxC,
                    weather.temperatureMinC,
                    weather.precipitationProbability,
                    weather.surfacePressureHpa,
                    weather.wmoCode,
                    location.lat,
                    location.lon
                )

                Log.d(TAG, "Weather cached: ${weather.temperatureC}°C, WMO=${weather.wmoCode}")

                updateWidgets(ctx)
                Result.success()

            } catch (e: Exception) {
                Log.e(TAG, "Worker error: ${e.message}", e)
                Result.retry()
            }
        }
    }

    private fun updateWidgets(ctx: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(ctx)
            val componentName = ComponentName(ctx, WeatherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                WidgetUpdater.updateAllWidgets(ctx, appWidgetManager, appWidgetIds)
                Log.d(TAG, "Updated ${appWidgetIds.size} widget(s)")
            } else {
                Log.d(TAG, "No active widgets to update")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widgets from worker: ${e.message}", e)
        }
    }
}
