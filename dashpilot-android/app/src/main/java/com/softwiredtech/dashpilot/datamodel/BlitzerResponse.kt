package com.softwiredtech.dashpilot.datamodel

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class BlitzerResponse(
    val pois: List<BlitzerPoi> = emptyList()
)

data class BlitzerPoi(
    val lat: String = "",
    val lng: String = "",
    val type: String = "",
    val address: JsonElement? = null,
    val vmax: String? = null,
    val info: BlitzerPoiInfo? = null,
    @SerializedName("backend") val backend: String? = null
)

data class BlitzerPoiInfo(
    val confirmed: String? = null
)
