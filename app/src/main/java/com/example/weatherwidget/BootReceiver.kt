package com.example.weatherwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Reschedules periodic updates after device reboot.
 * WorkManager generally survives reboots on its own, but explicit rescheduling
 * ensures updates resume correctly even after app updates.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot/package-replace received: ${intent.action}")
        try {
            val ctx = context.applicationContext

            // Only reschedule if there are active widget instances
            val appWidgetManager = AppWidgetManager.getInstance(ctx)
            val componentName = ComponentName(ctx, WeatherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isNotEmpty()) {
                Log.d(TAG, "Rescheduling for ${appWidgetIds.size} active widget(s)")
                WorkScheduler.scheduleWeatherUpdate(ctx)
                WorkScheduler.scheduleImmediateUpdate(ctx)
            } else {
                Log.d(TAG, "No active widgets, skipping reschedule")
            }
        } catch (e: Exception) {
            Log.e(TAG, "BootReceiver error: ${e.message}", e)
        }
    }
}
