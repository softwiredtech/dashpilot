package com.softwiredtech.dashpilot.datamodel.dash

import com.softwiredtech.dashkitconnect.CarState
import java.util.Locale

private const val KM_TO_MILES = 0.621371f
private val MILES_COUNTRIES = setOf("US", "GB", "MM", "LR")

fun speedLimitSignsInKm(): Boolean =
    Locale.getDefault().country !in MILES_COUNTRIES

fun CarState.toImperial(): CarState = copy(
    odometer = odometer * KM_TO_MILES,
    packTMin = if (packTMin != 0f) packTMin * 1.8f + 32f else 0f,
    packTMax = if (packTMax != 0f) packTMax * 1.8f + 32f else 0f,
    acTemp = if (acTemp != 0f) acTemp * 1.8f + 32f else 0f,
    fusedSpeedLimit = if (speedLimitSignsInKm()) fusedSpeedLimit * KM_TO_MILES else fusedSpeedLimit,
)
