package com.softwiredtech.dashpilot.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object LocationProvider {

    private const val TAG = "LocationProvider"

    fun locationFlow(context: Context): Flow<Location> = callbackFlow {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "hasFine=$hasFine hasCoarse=$hasCoarse")

        if (!hasFine && !hasCoarse) {
            Log.e(TAG, "No location permission granted — aborting")
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val listener = LocationListener { location ->
            Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude} provider=${location.provider}")
            trySend(location)
        }

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        Log.d(TAG, "GPS enabled=$gpsEnabled, Network enabled=$networkEnabled")

        val provider = when {
            hasFine && gpsEnabled -> LocationManager.GPS_PROVIDER
            networkEnabled -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            Log.e(TAG, "No location provider available")
            close()
            return@callbackFlow
        }

        Log.d(TAG, "Using provider: $provider")

        val lastKnown = locationManager.getLastKnownLocation(provider)
        if (lastKnown != null) {
            Log.d(TAG, "Last known location: ${lastKnown.latitude}, ${lastKnown.longitude}")
            trySend(lastKnown)
        } else {
            Log.w(TAG, "No last known location for $provider — waiting for first fix")
        }

        // minTime 30s, minDistance 500m
        locationManager.requestLocationUpdates(provider, 30_000L, 500f, listener)

        awaitClose { locationManager.removeUpdates(listener) }
    }
}
