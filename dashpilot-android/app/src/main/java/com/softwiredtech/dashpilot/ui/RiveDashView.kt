package com.softwiredtech.dashpilot.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.net.toUri
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFileSource
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorkerOrNull
import app.rive.rememberViewModelInstance
import com.softwiredtech.dashpilot.datamodel.dash.DASH_PREFS_NAME
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Date

private const val MODULAR_ASSET = "dashboard_modular"
private const val MODULAR_ARTBOARD_WIDTH = 1600f
private const val KM_TO_MILES = 0.621371f
private const val SLOT_COUNT = 5
private const val PREF_SLOT_PREFIX = "rive_modular_slot_"

@Composable
fun RiveDashView(
    assetName: String,
    dashStateFlow: Flow<DashState>,
    scope: CoroutineScope,
    onError: ((message: String) -> Unit),
    fileUri: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val context = LocalContext.current
        val errorState = remember { mutableStateOf<Throwable?>(null) }
        val riveWorker = rememberRiveWorkerOrNull(errorState)
        if (riveWorker == null) {
            onError(errorState.value.toString())
            return
        }

        val riveSource = remember(assetName, fileUri) {
            if (!fileUri.isNullOrEmpty()) {
                try {
                    val uri = fileUri.toUri()
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) RiveFileSource.Bytes(bytes) else null
                } catch (e: Exception) {
                    null
                }
            } else {
                val resId = context.resources.getIdentifier(assetName, "raw", context.packageName)
                RiveFileSource.RawRes(resId, context.resources)
            }
        }

        if (riveSource == null) {
            onError("Failed to read rive file from: $fileUri")
            return
        }

        when (val riveFile = rememberRiveFile(riveSource, riveWorker)) {
            is Result.Loading -> {
            }

            is Result.Error -> {
                onError(riveFile.throwable.toString())
            }

            is Result.Success -> {
                val file = riveFile.value
                val vmiSource = ViewModelSource.Named("MainViewModel").defaultInstance()
                val vmi = rememberViewModelInstance(file, vmiSource)
                val responsive = assetName == MODULAR_ASSET
                var scaleFactor by remember { mutableFloatStateOf(1f) }
                Rive(
                    file,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { scaleFactor = it.width / MODULAR_ARTBOARD_WIDTH },
                    viewModelInstance = vmi,
                    fit = if (responsive) Fit.Layout(scaleFactor) else Fit.Contain()
                )

                LaunchedEffect(vmi) {
                    val binder = DashViewModelBinder(vmi, context)
                    if (responsive) {
                        binder.restoreSlots()
                        launch { binder.persistSlots() }
                    }
                    scope.launch {
                        dashStateFlow.collect { binder.bind(it) }
                    }
                }
            }
        }
    }
}

private class DashViewModelBinder(
    private val vmi: ViewModelInstance,
    private val context: Context
) {
    private val missing = HashSet<String>()
    private val lastStrings = HashMap<String, String>()
    private var clockMinute = -1L
    private var clockText = ""

    fun bind(state: DashState) {
        val car = state.carState
        val imperial = state.displaySettings.useImperial
        val speedScale = if (imperial) KM_TO_MILES else 1f

        number("speed", car.egoSpeed * speedScale)
        number("speedLimit", car.fusedSpeedLimit)
        number("gear", car.gear)
        number("steeringAngle", car.egoSteeringAngle)
        number("blinkerLeft", car.leftBlinker)
        number("blinkerRight", car.rightBlinker)
        number("leftBlindspot", car.leftBlindSpot)
        number("rightBlindspot", car.rightBlindSpot)
        number("stopDist", car.stopLineDist)
        number("trafficLightColor", car.trafficLightColor)
        number("trafficLightDist", car.stopLineDist)
        number("accSetSpeed", car.accSetSpeed * speedScale)
        number("laneDeparture", car.laneDepartureWarning)
        number("sideCollision", car.sideCollisionWarning)
        number("anyDoorOpen", car.anyDoorOpen)
        number("buckleStatus", car.buckleStatus)
        number("adasOn", flag(car.adasOn || car.selfdriveActive))
        number("selfdriveActive", flag(car.selfdriveActive))
        number("experimentalMode", flag(car.experimentalMode))
        number("madsActive", flag(car.madsActive))
        number("changingLane", flag(car.changingLane))
        number("fullPackEnergy", car.fullPackEnergy)
        number("nominalEnergyRemaining", car.nominalEnergyRemaining)
        number("energyBuffer", car.energyBuffer)
        number("maxRegenPower", car.maxRegenPower)
        number("maxDischargePower", car.maxDischargePower)
        number("packVoltage", car.packVoltage)
        number("packCurrent", car.packCurrent)
        number("packTMin", car.packTMin)
        number("packTMax", car.packTMax)
        number("odometer", car.odometer)
        number("acTemp", car.acTemp)
        number("acTempRight", car.acTempRight)
        number("hvacFanLevel", car.hvacFanLevel)
        number("hvacPowerState", car.hvacPowerState)
        number("hvacAcMode", car.hvacAcMode)
        number("phoneBattery", state.phoneBattery.toFloat())
        number("rangeFactor", if (imperial) 4.0f else 6.5f)

        string("speedUnit", if (imperial) "mph" else "km/h")
        string("distUnit", if (imperial) "mi" else "km")
        string("tempUnit", if (imperial) "°F" else "°C")
        string("clock", clock(state.currentTime))
    }

    fun restoreSlots() {
        val prefs = context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        for (i in 1..SLOT_COUNT) {
            val saved = prefs.getFloat(PREF_SLOT_PREFIX + i, -1f)
            if (saved >= 0f) number("slot$i", saved)
        }
    }

    suspend fun persistSlots() {
        val prefs = context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        for (i in 1..SLOT_COUNT) {
            val flow = runCatching { vmi.getNumberFlow("slot$i") }.getOrNull() ?: continue
            flow.collect { value -> prefs.edit { putFloat(PREF_SLOT_PREFIX + i, value) } }
        }
    }

    private fun clock(timeMillis: Long): String {
        val minute = timeMillis / 60_000L
        if (minute != clockMinute) {
            clockMinute = minute
            clockText = DateFormat.getTimeFormat(context).format(Date(timeMillis))
        }
        return clockText
    }

    private fun flag(value: Boolean) = if (value) 1f else 0f

    private fun number(name: String, value: Float) {
        if (name in missing) return
        try {
            vmi.setNumber(name, value)
        } catch (e: Exception) {
            missing += name
        }
    }

    private fun string(name: String, value: String) {
        if (name in missing || lastStrings[name] == value) return
        try {
            vmi.setString(name, value)
            lastStrings[name] = value
        } catch (e: Exception) {
            missing += name
        }
    }
}
