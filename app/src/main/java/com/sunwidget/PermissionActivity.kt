package com.sunwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Transparent trampoline activity.
 *
 * Launched automatically when the widget is first added to the home screen.
 * Requests ACCESS_FINE_LOCATION so the NOAA calculator can use the device's
 * actual coordinates. Finishes immediately after the permission dialog is
 * dismissed and triggers a widget refresh.
 */
class PermissionActivity : AppCompatActivity() {

    companion object {
        private const val REQ_LOCATION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine) {
            refreshAndFinish()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQ_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) refreshAndFinish()
    }

    private fun refreshAndFinish() {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, SunWidgetProvider::class.java))
        ids.forEach { SunWidgetProvider.updateWidget(this, mgr, it) }
        SunAlarmReceiver.schedule(this)
        finish()
    }
}
