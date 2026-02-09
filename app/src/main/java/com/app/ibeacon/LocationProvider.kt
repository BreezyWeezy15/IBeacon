package com.app.ibeacon

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

class LocationProvider(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest: LocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 5000L
    ).setMinUpdateIntervalMillis(3000L)
        .setMaxUpdateDelayMillis(10000L)
        .build()

    private var locationCallback: LocationCallback? = null

    var lastLocation: Location? = null
        private set

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(onLocationUpdated: ((Location) -> Unit)? = null) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Try last known location first
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                lastLocation = loc
                onLocationUpdated?.invoke(loc) // Immediately center map
            }
        }

        // Then check if GPS is enabled and start updates
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            startFusedUpdates(onLocationUpdated)
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && context is Activity) {
                try {
                    exception.startResolutionForResult(context, 9001)
                } catch (sendEx: IntentSender.SendIntentException) {
                    sendEx.printStackTrace()
                }
            }
        }
    }


    @Suppress("MissingPermission")
    private fun startFusedUpdates(onLocationUpdated: ((Location) -> Unit)?) {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    lastLocation = loc
                    onLocationUpdated?.invoke(loc)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }
}