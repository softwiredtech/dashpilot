package com.softwiredtech.dashpilot.api

import com.softwiredtech.dashpilot.datamodel.SpeedCamera
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.PI

object BlitzerRepository {

    private const val BASE_URL = "https://cdn2.atudo.net/api/4.0/"

    private const val POI_TYPES =
        "0,1,2,3,4,5,6,ts,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,117"

    private val api: BlitzerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BlitzerApiService::class.java)
    }

    suspend fun fetchNearby(lat: Double, lng: Double, radiusKm: Double = 5.0): List<SpeedCamera> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(lat * PI / 180.0))

        val lowLat = lat - latDelta
        val highLat = lat + latDelta
        val lowLng = lng - lngDelta
        val highLng = lng + lngDelta

        val box = "%.6f,%.6f,%.6f,%.6f".format(lowLat, lowLng, highLat, highLng)

        val response = api.getPois(type = POI_TYPES, box = box)

        return response.pois
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
    }
}
