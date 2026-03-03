package com.softwiredtech.dashpilot.vehicle

import android.content.Context
import kotlinx.serialization.json.Json

object VehicleProfileLoader {

    fun loadProfile(context: Context, vehicleName: String): VehicleProfile {
        val basePath = "vehicles/$vehicleName"
        val jsonContent = context.assets.open("$basePath/vehicle.json")
            .bufferedReader().use { it.readText() }

        val config = Json.decodeFromString<VehicleProfileConfig>(jsonContent)

        val busIndices = IntArray(config.buses.size)
        val dbcContents = Array(config.buses.size) { "" }

        for ((i, bus) in config.buses.withIndex()) {
            busIndices[i] = bus.index
            dbcContents[i] = context.assets.open("$basePath/${bus.dbc}")
                .bufferedReader().use { it.readText() }
        }

        return VehicleProfile(
            name = config.name,
            type = config.type,
            busIndices = busIndices,
            dbcContents = dbcContents
        )
    }

    fun listVehicles(context: Context): List<String> {
        return context.assets.list("vehicles")?.toList() ?: emptyList()
    }
}
