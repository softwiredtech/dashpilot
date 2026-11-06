package com.softwiredtech.dashpilot.ui

import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.datamodel.dash.DashColor
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datamodel.dash.dashValues
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AmbientLightingBindingTest {
    private fun snapshot(red: Int, green: Int, blue: Int, brightness: Int = 100, mask: Int = 53): DashState {
        val values = DoubleArray(CarState.FIELD_COUNT)
        values[39] = 2.5 // Existing TPMS slots must stay independent of ambient lighting.
        values[46] = 31.0
        values[58] = 1.0
        values[59] = red.toDouble()
        values[60] = green.toDouble()
        values[61] = blue.toDouble()
        values[62] = brightness.toDouble()
        values[63] = 165.0
        values[64] = mask.toDouble()
        return DashState(carState = CanFrameDecoder.arrayToCarState(values))
    }

    @Test fun candidate_state_and_hex_edge_cases() {
        val state = snapshot(18, 52, 86).carState
        assertEquals("#123456", state.ambientLightingHex)
        assertEquals(100f, state.ambientLightingBrightness)
        assertEquals(165f, state.ambientLightingEnableEffectRaw)
        assertEquals(53f, state.ambientLightingSectionMaskRaw)
        assertEquals(2.5f, state.tpmsPressureFL)
        assertEquals(31f, state.tpmsTempRR)
        assertEquals(state.ambientLightingHex, state.toImperial().ambientLightingHex)
        assertEquals("#01020A", snapshot(1, 2, 10).carState.ambientLightingHex)
        assertEquals("#000000", snapshot(0, 0, 0).carState.ambientLightingHex)
        assertEquals("#FFFFFF", snapshot(255, 255, 255).carState.ambientLightingHex)
        assertEquals(DashColor(255, 255, 255), snapshot(255, 255, 255, 0).dashValues()["ambientLightingColor"])
        val unknown = CanFrameDecoder.arrayToCarState(DoubleArray(CarState.FIELD_COUNT))
        assertFalse(unknown.ambientLightingAvailable)
        assertEquals("", unknown.ambientLightingHex)
        assertFalse(DashState(unknown).dashValues().containsKey("ambientLightingColor"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun live_updates_and_mount_replay_dispatch_typed_opaque_colors() = runTest {
        // DashKit's telemetry stream replays the latest snapshot to each mount.
        val stream = MutableSharedFlow<DashState>(replay = 1)
        val properties = mapOf(
            "ambientLightingColor" to PropertyDataType.COLOR,
            "ambientLightingHex" to PropertyDataType.STRING,
            "ambientLightingBrightness" to PropertyDataType.NUMBER,
            "ambientLightingSectionMaskRaw" to PropertyDataType.NUMBER,
            "ambientLightingAvailable" to PropertyDataType.BOOLEAN,
        )
        val writes = mutableMapOf<String, Any>()
        stream.emit(snapshot(18, 52, 86))
        fun mount(target: MutableMap<String, Any>) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            stream.collect { state ->
                applyRiveDashValues(properties, state.dashValues(),
                    { k, v -> target[k] = v }, { k, v -> target[k] = v },
                    { k, v -> target[k] = v }, { k, v -> target[k] = v })
            }
        }
        val firstMount = mount(writes)
        assertEquals(0xFF123456.toInt(), writes["ambientLightingColor"])
        assertEquals(100f, writes["ambientLightingBrightness"])
        stream.emit(snapshot(255, 255, 255, brightness = 0, mask = 1))
        assertEquals(0xFFFFFFFF.toInt(), writes["ambientLightingColor"])
        assertEquals(0f, writes["ambientLightingBrightness"])
        assertEquals(1f, writes["ambientLightingSectionMaskRaw"])
        firstMount.cancel()
        val remounted = mutableMapOf<String, Any>()
        mount(remounted)
        assertEquals(writes, remounted)
        assertFalse(writes.containsKey("egoSpeed")) // undeclared bindings skipped
    }
}
