package com.example.weatherwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * AppWidgetProvider - the core widget BroadcastReceiver.
 *
 * CRITICAL RULES to prevent "Can't Load Widget":
 * 1. onUpdate MUST complete quickly — no network, no heavy work
 * 2. All heavy work goes to WorkManager
 * 3. Every method must be wrapped in try/catch
 * 4. Never hold static references to Context
 * 5. Use applicationContext everywhere
 * 6. RemoteViews pkg name must match app packageName exactly
 * 7. Widget layout must use only RemoteViews-compatible view types
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "WeatherWidgetProvider"
        const val ACTION_TOGGLE_LOCATION = "com.example.weatherwidget.ACTION_TOGGLE_LOCATION"
        const val ACTION_TOGGLE_UNITS = "com.example.weatherwidget.ACTION_TOGGLE_UNITS"
        const val ACTION_WEATHER_UPDATED = "com.example.weatherwidget.ACTION_WEATHER_UPDATED"
    }

    /**
     * Called when widget needs to be updated (initial add, or updatePeriodMillis elapsed).
     * We set updatePeriodMillis=0 so this is only called on initial add/screen on.
     * Actual refresh is handled by WorkManager.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widget(s)")
        try {
            val ctx = context.applicationContext
            // Update UI immediately with cached data (or loading state)
            WidgetUpdater.updateAllWidgets(ctx, appWidgetManager, appWidgetIds)
            // Then kick off a background refresh
            WorkScheduler.scheduleImmediateUpdate(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "onUpdate error: ${e.message}", e)
        }
    }

    /**
     * Called once when the FIRST widget instance is added to the home screen.
     */
    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled - first widget instance added")
        try {
            WorkScheduler.scheduleWeatherUpdate(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "onEnabled error: ${e.message}", e)
        }
    }

    /**
     * Called when the LAST widget instance is removed.
     */
    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled - last widget instance removed")
        try {
            WorkScheduler.cancelWeatherUpdate(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "onDisabled error: ${e.message}", e)
        }
    }

    /**
     * Called for each deleted widget instance.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted: ${appWidgetIds.joinToString()}")
        // Nothing to clean up per-instance for this widget
    }

    /**
     * Receives broadcast intents — handles our custom actions.
     */
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")
        try {
            val ctx = context.applicationContext
            when (intent.action) {
                ACTION_TOGGLE_LOCATION -> handleToggleLocation(ctx)
                ACTION_TOGGLE_UNITS -> handleToggleUnits(ctx)
                ACTION_WEATHER_UPDATED -> handleWeatherUpdated(ctx)
                else -> super.onReceive(context, intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onReceive error: ${e.message}", e)
            super.onReceive(context, intent)
        }
    }

    private fun handleToggleLocation(ctx: Context) {
        val currentlyUsingHome = Prefs.isUseHome(ctx)

        if (!currentlyUsingHome) {
            // Trying to switch TO home
            if (!Prefs.hasHomeLocation(ctx)) {
                // No home set — launch dialog activity
                val dialogIntent = Intent(ctx, HomeLocationActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                ctx.startActivity(dialogIntent)
                return
            }
            Prefs.setUseHome(ctx, true)
        } else {
            Prefs.setUseHome(ctx, false)
        }

        // Refresh immediately after toggle
        WorkScheduler.scheduleImmediateUpdate(ctx)
        refreshAllWidgets(ctx)
    }

    private fun handleToggleUnits(ctx: Context) {
        val wasCelsius = Prefs.isUseCelsius(ctx)
        Prefs.setUseCelsius(ctx, !wasCelsius)
        refreshAllWidgets(ctx)
    }

    private fun handleWeatherUpdated(ctx: Context) {
        refreshAllWidgets(ctx)
    }

    private fun refreshAllWidgets(ctx: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(ctx)
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(ctx, WeatherWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                WidgetUpdater.updateAllWidgets(ctx, appWidgetManager, ids)
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAllWidgets error: ${e.message}", e)
        }
    }
}
