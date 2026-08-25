package com.softwiredtech.dashpilot.ui.tesla

import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.vehicle.VehicleVinState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeslaEnrollmentControllerTest {
    private val vin = "5YJ3E7EB1MF123456"
    private val mac = "AA:BB:CC:DD:EE:FF"

    @Test
    fun stages_vehicle_before_explicit_connect() = runTest {
        val vinState = MutableStateFlow<VehicleVinState>(VehicleVinState.Available(vin))
        val status = MutableStateFlow(TeslaStatus.Idle)
        val provisions = mutableListOf<Pair<String, String>>()
        var starts = 0
        val controller = controller(
            vinState = vinState,
            status = status,
            provision = { stagedVin, stagedMac ->
                provisions += stagedVin to stagedMac
                status.value = status.value.copy(linkState = TeslaLinkState.Staged)
                true
            },
            startEnrollment = {
                starts++
                true
            },
        ).also { it.begin() }

        advanceUntilIdle()
        assertTrue(controller.state.value is TeslaEnrollmentState.ReadyToConnect)
        assertEquals(listOf(vin to mac), provisions)
        assertEquals(0, starts)

        controller.connect()
        assertEquals(1, starts)
        assertTrue(controller.state.value is TeslaEnrollmentState.Connecting)
    }

    @Test
    fun gatt_dispatch_is_not_staging_acknowledgement() = runTest {
        val vinState = MutableStateFlow<VehicleVinState>(VehicleVinState.Available(vin))
        val status = MutableStateFlow(TeslaStatus.Idle)
        val controller = controller(
            vinState = vinState,
            status = status,
            provision = { _, _ -> true },
        ).also { it.begin() }

        advanceUntilIdle()

        val error = controller.state.value as TeslaEnrollmentState.Error
        assertEquals(TeslaEnrollmentErrorReason.NotAcknowledged, error.reason)
    }

    @Test
    fun missing_vin_never_provisions() = runTest {
        val vinState = MutableStateFlow<VehicleVinState>(VehicleVinState.Waiting)
        val status = MutableStateFlow(TeslaStatus.Idle)
        var provisions = 0
        val controller = controller(
            vinState = vinState,
            status = status,
            provision = { _, _ ->
                provisions++
                true
            },
        ).also { it.begin() }

        advanceUntilIdle()

        assertEquals(0, provisions)
        val error = controller.state.value as TeslaEnrollmentState.Error
        assertEquals(TeslaEnrollmentErrorReason.VinUnavailable, error.reason)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        vinState: MutableStateFlow<VehicleVinState>,
        status: MutableStateFlow<TeslaStatus>,
        provision: (String, String) -> Boolean,
        startEnrollment: () -> Boolean = { true },
    ) = TeslaEnrollmentController(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        vinState = vinState,
        status = status,
        matchScanner = { flowOf(mac) },
        hasTeslaService = { true },
        provision = provision,
        startEnrollment = startEnrollment,
        cancelPairing = {},
    )
}
