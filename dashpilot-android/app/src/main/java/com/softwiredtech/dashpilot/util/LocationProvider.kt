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

        val client = LocationServices.getFusedLocationProviderClient(context)

        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY
                       else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val request = LocationRequest.Builder(priority, 30_000L).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                trySend(location)
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnSuccessListener {
                Log.d(TAG, "Registered for fused location updates (interval=30s)")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register location updates: ${e.message}", e)
                close(e)
            }

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}
