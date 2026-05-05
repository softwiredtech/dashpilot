package com.softwiredtech.dashpilot.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

object LocationProvider {

    private const val TAG = "LocationProvider"
    const val INTERVAL_NORMAL = 20_000L
    const val INTERVAL_ALERT = 2_000L

    fun locationFlow(
        context: Context,
        intervalMs: StateFlow<Long>
    ): Flow<Location> = callbackFlow {
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

        val client = try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            Log.e(TAG, "GMS LocationServices not available: ${e.message}")
            close()
            return@callbackFlow
        }

        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY
                       else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location == null) {
                    Log.w(TAG, "LocationResult received but lastLocation is null (${result.locations.size} locations)")
                    return
                }
                Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                trySend(location)
            }
        }

        var currentInterval = -1L

        launch {
            intervalMs.collect { newInterval ->
                if (newInterval == currentInterval) return@collect
                currentInterval = newInterval
                Log.d(TAG, "Switching location interval to ${newInterval}ms")
                val request = LocationRequest.Builder(priority, newInterval).build()
                client.removeLocationUpdates(callback)
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to register location updates: ${e.message}", e)
                        close(e)
                    }
            }
        }

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}
