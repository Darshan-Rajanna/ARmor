package com.example.armor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat

class LocationHelper(private val context: Context) {

    interface LocationCallback {
        fun onLocationResult(location: Location)
        fun onLocationUnavailable(reason: String)
    }

    private val timeoutMillis = 10_000L // 10 seconds

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(callback: LocationCallback) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val hasFineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            callback.onLocationUnavailable("Location permission not granted")
            return
        }

        var locationDelivered = false
        val handler = Handler(Looper.getMainLooper())

        // Declare timeoutRunnable as nullable and late-init
        var timeoutRunnable: Runnable? = null

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!locationDelivered) {
                    locationDelivered = true
                    callback.onLocationResult(location)
                    locationManager.removeUpdates(this)
                    timeoutRunnable?.let { handler.removeCallbacks(it) }
                }
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        timeoutRunnable = Runnable {
            if (!locationDelivered) {
                locationDelivered = true
                locationManager.removeUpdates(locationListener)
                callback.onLocationUnavailable("Timed out waiting for location")
            }
        }

        handler.postDelayed(timeoutRunnable, timeoutMillis)

        // Try last known location as fallback
        val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (lastKnownLocation != null && !locationDelivered) {
            locationDelivered = true
            handler.removeCallbacks(timeoutRunnable)
            callback.onLocationResult(lastKnownLocation)
        }
    }
}
