package com.softwiredtech.dashpilot.api

import android.location.Location
import android.util.Log
import com.softwiredtech.dashpilot.datamodel.api.SpeedCamera
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.cos
import kotlin.math.PI

// Based on: https://github.com/timniklas/hass-blitzerde
object BlitzerRepository {
    private const val TAG = "BlitzerRepository"
    private const val BASE_URL = "https://cdn2.atudo.net/api/4.0/"
    private const val SEARCH_RADIUS_KM = 5.0
    private const val CACHE_THRESHOLD_KM = 3.0

    private const val POI_TYPES =
        "0,1,2,3,4,5,6,ts,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,117"

    private val api: BlitzerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BlitzerApiService::class.java)
    }

    private var lastFetchLat: Double = Double.NaN
    private var lastFetchLng: Double = Double.NaN
    private var cachedCameras: List<SpeedCamera> = emptyList()

    suspend fun fetchNearby(lat: Double, lng: Double): List<SpeedCamera> {
        if (isCacheValid(lat, lng)) {
            Log.d(TAG, "Using cached cameras (${cachedCameras.size})")
            return cachedCameras
        }

        val latDelta = SEARCH_RADIUS_KM / 111.0
        val lngDelta = SEARCH_RADIUS_KM / (111.0 * cos(lat * PI / 180.0))
        val box = "%.6f,%.6f,%.6f,%.6f".format(lat - latDelta, lng - lngDelta, lat + latDelta, lng + lngDelta)

        val response = api.getPois(type = POI_TYPES, box = box)

        val cameras = response.pois
            .filter { it.type != "cluster" }
            .mapNotNull { poi ->
                val poiLat = poi.lat.toDoubleOrNull() ?: return@mapNotNull null
                val poiLng = poi.lng.toDoubleOrNull() ?: return@mapNotNull null
                SpeedCamera(
                    lat = poiLat,
                    lng = poiLng,
                    type = poi.type,
                    address = poi.address?.let {
                        if (it.isJsonPrimitive) it.asString else it.toString()
                    },
                    vmax = poi.vmax,
                    confirmed = poi.info?.confirmed
                )
            }

        lastFetchLat = lat
        lastFetchLng = lng
        cachedCameras = cameras
        Log.d(TAG, "Fetched ${cameras.size} cameras near $lat, $lng")
        return cameras
    }

    private fun isCacheValid(lat: Double, lng: Double): Boolean {
        if (lastFetchLat.isNaN()) return false
        val results = FloatArray(1)
        Location.distanceBetween(lastFetchLat, lastFetchLng, lat, lng, results)
        return results[0] < CACHE_THRESHOLD_KM * 1000
    }

    fun clearCache() {
        lastFetchLat = Double.NaN
        lastFetchLng = Double.NaN
        cachedCameras = emptyList()
    }
}
