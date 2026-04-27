package com.softwiredtech.dashpilot.datamodel.api

data class SpeedCamera(
    val lat: Double,
    val lng: Double,
    val type: String,
    val address: String?,
    val vmax: String?,
    val confirmed: String?
)