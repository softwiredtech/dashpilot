package com.softwiredtech.dashpilot.vehicle

sealed interface VehicleVinState {
    data object Waiting : VehicleVinState
    data class Available(val vin: String) : VehicleVinState
    data object Invalid : VehicleVinState
}
