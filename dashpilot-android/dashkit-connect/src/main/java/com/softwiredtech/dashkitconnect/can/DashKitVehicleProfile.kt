package com.softwiredtech.dashkitconnect.can

import android.content.Context

/**
 * Which DBC file decodes each CAN bus the DashKit forwards, and which
 * native CarState mapper interprets the signals. [Tesla] ships with the
 * library; supply your own to decode a different vehicle.
 */
data class DashKitVehicleProfile(
    val vehicleType: String,
    val buses: List<Bus>,
) {
    data class Bus(val index: Int, val dbcAssetPath: String)

    internal fun loadDbcContents(context: Context): Array<String> =
        Array(buses.size) { i ->
            context.assets.open(buses[i].dbcAssetPath).bufferedReader().use { it.readText() }
        }

    internal val busIndices: IntArray get() = IntArray(buses.size) { buses[it].index }

    companion object {
        const val TESLA_DBC_ASSET = "dashkit-connect/bus_1_tesla_vehicle.dbc"

        val Tesla = DashKitVehicleProfile(
            vehicleType = "dashkit",
            buses = listOf(Bus(0, TESLA_DBC_ASSET), Bus(1, TESLA_DBC_ASSET)),
        )
    }
}
