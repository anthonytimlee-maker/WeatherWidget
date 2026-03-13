package com.example.weatherwidget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Manages WorkManager scheduling for periodic weather updates.
 */
object WorkScheduler {

    private const val TAG = "WorkScheduler"

    /**
     * Schedule a periodic weather update every 15 minutes.
     * Uses KEEP policy: if work already scheduled, don't replace it.
     * Call this from widget onEnabled and after device boot.
     */
    fun scheduleWeatherUpdate(context: Context) {
        Log.d(TAG, "Scheduling periodic weather update (15 min)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            15, TimeUnit.MINUTES,
            // Flex period: work can run in last 5 minutes of each 15 minute window
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                WeatherUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    /**
     * Schedule an immediate one-time update (e.g., widget just added or toggled).
     */
    fun scheduleImmediateUpdate(context: Context) {
        Log.d(TAG, "Scheduling immediate weather update")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueue(workRequest)
    }

    /**
     * Cancel periodic updates (called when all widget instances are removed).
     */
    fun cancelWeatherUpdate(context: Context) {
        Log.d(TAG, "Cancelling periodic weather update")
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(WeatherUpdateWorker.WORK_NAME)
    }
}
