package com.softwiredtech.dashpilot.vehicle

import android.content.Context
import kotlinx.serialization.json.Json

object VehicleProfileLoader {

    fun loadProfile(context: Context, vehicleName: String, configFile: String = "config_comma_normal.json"): VehicleProfile {
        val basePath = "vehicles/$vehicleName"
        val jsonContent = context.assets.open("$basePath/$configFile")
            .bufferedReader().use { it.readText() }

        val config = Json.decodeFromString<VehicleProfileConfig>(jsonContent)

        val busIndices = IntArray(config.buses.size)
        val dbcContents = Array(config.buses.size) { "" }

        for ((i, bus) in config.buses.withIndex()) {
            busIndices[i] = bus.index
            val dbcPath = if (bus.dbc.startsWith("/")) bus.dbc.substring(1) else "$basePath/${bus.dbc}"
            dbcContents[i] = context.assets.open(dbcPath)
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
