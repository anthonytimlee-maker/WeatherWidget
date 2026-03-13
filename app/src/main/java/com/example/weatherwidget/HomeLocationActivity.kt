package com.example.weatherwidget

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeLocationActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var fetchedLat = 0.0
    private var fetchedLon = 0.0
    private var locationFetched = false
    private var dialog: AlertDialog? = null
    private var tvLocation: TextView? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialogView = layoutInflater.inflate(R.layout.activity_home_location, null)
        tvLocation = dialogView.findViewById(R.id.tv_current_location)

        // Use plain android.app.AlertDialog — no Material dependency
        val builtDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_home_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_as_home), null)
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
            .setCancelable(true)
            .create()

        builtDialog.setOnDismissListener { finish() }
        builtDialog.show()
        dialog = builtDialog

        val positiveBtn: Button? = builtDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn?.isEnabled = false
        positiveBtn?.setOnClickListener {
            if (locationFetched) {
                Prefs.setHomeLocation(applicationContext, fetchedLat, fetchedLon)
                Prefs.setUseHome(applicationContext, true)
                Toast.makeText(this, getString(R.string.location_saved), Toast.LENGTH_SHORT).show()
                WorkScheduler.scheduleImmediateUpdate(applicationContext)
                builtDialog.dismiss()
            }
        }

        if (hasLocationPermission()) {
            fetchLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun fetchLocation() {
        tvLocation?.text = getString(R.string.fetching_location)
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        activityScope.launch {
            val location = withContext(Dispatchers.IO) {
                LocationHelper.getLocation(applicationContext)
            }
            if (!isFinishing && !isDestroyed) {
                if (location != null) {
                    fetchedLat = location.lat
                    fetchedLon = location.lon
                    locationFetched = true
                    tvLocation?.text = "${"%.5f".format(location.lat)}, ${"%.5f".format(location.lon)}"
                    dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                } else {
                    tvLocation?.text = getString(R.string.no_location)
                }
            }
        }
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                tvLocation?.text = getString(R.string.permission_needed)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        dialog = null
        activityScope.coroutineContext[Job]?.cancel()
    }
}
