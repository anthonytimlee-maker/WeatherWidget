package com.example.weatherwidget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Wraps location retrieval.
 * Uses FusedLocationProviderClient for best accuracy/battery balance.
 * Falls back to last known location if current request times out.
 *
 * IMPORTANT: Must be called from a background thread (uses Tasks.await).
 */
object LocationHelper {

    private const val TAG = "LocationHelper"

    data class LatLon(val lat: Double, val lon: Double)

    /**
     * Get the best available location.
     * Returns null if permission denied or location unavailable.
     * Must be called on a background thread.
     */
    fun getLocation(context: Context): LatLon? {
        if (!hasPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

            // Try current location first with a short timeout
            try {
                val task = fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                )
                val location: Location? = Tasks.await(task, 10, TimeUnit.SECONDS)
                if (location != null) {
                    Log.d(TAG, "Got current location: ${location.latitude}, ${location.longitude}")
                    return LatLon(location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                Log.w(TAG, "getCurrentLocation failed, trying lastLocation: ${e.message}")
            }

            // Fall back to last known location
            val lastTask = fusedClient.lastLocation
            val lastLocation: Location? = Tasks.await(lastTask, 5, TimeUnit.SECONDS)
            if (lastLocation != null) {
                Log.d(TAG, "Got last location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                LatLon(lastLocation.latitude, lastLocation.longitude)
            } else {
                Log.w(TAG, "No location available")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}", e)
            null
        }
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
