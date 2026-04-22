package com.softwiredtech.dashpilot.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softwiredtech.dashpilot.api.BlitzerRepository
import com.softwiredtech.dashpilot.datamodel.SpeedCamera
import com.softwiredtech.dashpilot.util.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeedCameraViewModel : ViewModel() {

    private val _nearbyCameras = MutableStateFlow<List<SpeedCamera>>(emptyList())
    val nearbyCameras: StateFlow<List<SpeedCamera>> = _nearbyCameras.asStateFlow()

    private var locationJob: Job? = null

    fun startUpdating(context: Context) {
        if (locationJob?.isActive == true) return

        locationJob = viewModelScope.launch {
            LocationProvider.locationFlow(context).collect { location ->
                fetchAndLog(location.latitude, location.longitude, "GPS")
            }
        }
    }

    fun startWithMocks() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            val mocks = listOf(
                Triple(46.0727, 18.2323, "Pécs"),
                Triple(50.1109, 8.6821, "Frankfurt")
            )
            for ((lat, lng, name) in mocks) {
                fetchAndLog(lat, lng, name)
            }
        }
    }

    private suspend fun fetchAndLog(lat: Double, lng: Double, label: String = "") {
        try {
            val cameras = BlitzerRepository.fetchNearby(lat, lng)
            _nearbyCameras.value = cameras
            Log.d("SpeedCameraVM", "[$label] Fetched ${cameras.size} cameras near $lat, $lng")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SpeedCameraVM", "Failed to fetch cameras: ${e.message}")
        }
    }

    fun stopUpdating() {
        locationJob?.cancel()
        locationJob = null
        _nearbyCameras.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopUpdating()
    }
}
