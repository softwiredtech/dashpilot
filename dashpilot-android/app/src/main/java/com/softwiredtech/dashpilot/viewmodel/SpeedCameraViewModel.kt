package com.softwiredtech.dashpilot.viewmodel

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softwiredtech.dashpilot.api.BlitzerRepository
import com.softwiredtech.dashpilot.datamodel.api.SpeedCamera
import com.softwiredtech.dashpilot.util.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class SpeedCameraViewModel : ViewModel() {

    private val _nearbyCameras = MutableStateFlow<List<SpeedCamera>>(emptyList())

    private val _nearestApproachingCamera = MutableStateFlow<Pair<SpeedCamera, Int>?>(null)
    val nearestApproachingCamera: StateFlow<Pair<SpeedCamera, Int>?> = _nearestApproachingCamera.asStateFlow()

    private val locationInterval = MutableStateFlow(LocationProvider.INTERVAL_NORMAL)
    private val previousDistances = mutableMapOf<String, Float>()
    private var locationJob: Job? = null

    fun startUpdating(context: Context) {
        if (locationJob?.isActive == true) return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            try {
                LocationProvider.locationFlow(context, locationInterval).collect { location ->
                    processLocation(location)
                }
                Log.w("SpeedCameraVM", "Location flow completed (permissions missing or GMS unavailable)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SpeedCameraVM", "Location flow error: ${e.message}", e)
            }
        }
    }

    private suspend fun processLocation(location: Location) {
        try {
            val cameras = BlitzerRepository.fetchNearby(location.latitude, location.longitude)
            _nearbyCameras.value = cameras
            updateApproaching(location, cameras)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SpeedCameraVM", "Failed to fetch cameras: ${e.message}")
        }
    }

    private fun updateApproaching(location: Location, cameras: List<SpeedCamera>) {
        val results = FloatArray(2)
        var anyCameraWithinRange = false

        val nearest = cameras
            .mapNotNull { cam ->
                Location.distanceBetween(location.latitude, location.longitude, cam.lat, cam.lng, results)
                val dist = results[0]
                if (dist > 1000f) return@mapNotNull null

                anyCameraWithinRange = true
                val camBearing = results[1]
                val key = "${cam.lat},${cam.lng}"

                val approaching = if (location.hasBearing()) {
                    var diff = abs((camBearing - location.bearing + 360f) % 360f)
                    if (diff > 180f) diff = 360f - diff
                    diff < 90f
                } else {
                    val prev = previousDistances[key]
                    prev != null && dist < prev
                }

                previousDistances[key] = dist

                // Alert if approaching within 500m, or unconditionally within 100m
                if (dist <= 100f || (approaching && dist <= 500f)) cam to dist.toInt() else null
            }
            .minByOrNull { (_, d) -> d }

        _nearestApproachingCamera.value = nearest

        val newInterval = if (anyCameraWithinRange) LocationProvider.INTERVAL_ALERT
                          else LocationProvider.INTERVAL_NORMAL
        if (locationInterval.value != newInterval) {
            Log.d("SpeedCameraVM", "Switching to ${if (anyCameraWithinRange) "alert" else "normal"} interval")
        }
        locationInterval.value = newInterval

        if (nearest != null) {
            Log.d("SpeedCameraVM", "Alert: ${nearest.second}m ahead")
        }
    }

    fun stopUpdating() {
        locationJob?.cancel()
        locationJob = null
        _nearbyCameras.value = emptyList()
        _nearestApproachingCamera.value = null
        locationInterval.value = LocationProvider.INTERVAL_NORMAL
        previousDistances.clear()
        BlitzerRepository.clearCache()
    }

    override fun onCleared() {
        super.onCleared()
        stopUpdating()
    }
}
