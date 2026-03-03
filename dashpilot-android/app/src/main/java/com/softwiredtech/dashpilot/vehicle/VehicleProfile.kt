package com.softwiredtech.dashpilot.vehicle

import kotlinx.serialization.Serializable

@Serializable
data class BusConfig(
    val index: Int,
    val dbc: String
)

@Serializable
data class VehicleProfileConfig(
    val name: String,
    val type: String,
    val buses: List<BusConfig>
)

data class VehicleProfile(
    val name: String,
    val type: String,
    val busIndices: IntArray,
    val dbcContents: Array<String>
)
