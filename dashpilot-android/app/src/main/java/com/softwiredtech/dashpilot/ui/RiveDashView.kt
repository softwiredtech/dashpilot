package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.ViewModelInstance
import app.rive.ViewModelSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorkerOrNull
import app.rive.rememberViewModelInstance
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min


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
        val myFile: MutableState<RiveFile?> = remember { mutableStateOf(null) }

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
                val vmiSource = ViewModelSource.Named(MAIN_VIEW_MODEL).defaultInstance()
                val vmi = rememberViewModelInstance(file, vmiSource)
                Rive(
                    riveFile.value,
                    viewModelInstance = vmi
                )
                myFile.value = riveFile.value

                LaunchedEffect(Unit) {
                    scope.launch {
                        val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        dashStateFlow.collect { dashState ->
                            bindDashState(vmi, dashState, clockFormat)
                        }
                    }
                }
            }
        }
    }
}

private const val MAIN_VIEW_MODEL = "MainViewModel"
private const val KM_TO_MILES = 0.621371f
private const val METERS_TO_FEET = 3.28084f

// Pushes DashState into the Rive "MainViewModel" (see dash-apps/rive).
// CarState arrives with odometer, temperatures and speed limit already
// converted for imperial; speeds and distances are converted here.
private fun bindDashState(vmi: ViewModelInstance, dashState: DashState, clockFormat: SimpleDateFormat) {
    val car = dashState.carState
    val imperial = dashState.displaySettings.useImperial
    val speedFactor = if (imperial) KM_TO_MILES else 1f
    val distFactor = if (imperial) METERS_TO_FEET else 1f

    vmi.setString("speedUnit", if (imperial) "mph" else "km/h")
    vmi.setString("distUnitLarge", if (imperial) "mi" else "km")
    vmi.setString("distUnitSmall", if (imperial) "ft" else "m")
    vmi.setString("tempUnit", if (imperial) "°F" else "°C")
    vmi.setString("clock", clockFormat.format(Date(dashState.currentTime)))

    vmi.setNumber("speed", car.egoSpeed * speedFactor)
    vmi.setNumber("speedLimit", car.fusedSpeedLimit)
    vmi.setNumber("accSpeed", car.accSetSpeed * speedFactor)
    vmi.setBoolean("accState", car.adasOn)
    vmi.setNumber("gear", car.gear)
    vmi.setNumber("steeringAngle", car.egoSteeringAngle)
    vmi.setNumber("blinkerLeft", car.leftBlinker)
    vmi.setNumber("blinkerRight", car.rightBlinker)
    vmi.setNumber("leftBlindspot", car.leftBlindSpot)
    vmi.setNumber("rightBlindspot", car.rightBlindSpot)
    vmi.setNumber("stopDist", car.stopLineDist * distFactor)
    vmi.setNumber("trafficLightColor", car.trafficLightColor)
    vmi.setNumber("speedCam", speedCamDistance(dashState.speedCameraDistance, distFactor))
    vmi.setNumber("laneDepartureWarning", car.laneDepartureWarning)
    vmi.setNumber("sideCollisionWarning", car.sideCollisionWarning)
    vmi.setNumber("anyDoorOpen", car.anyDoorOpen)
    vmi.setNumber("buckleStatus", car.buckleStatus)
    vmi.setBoolean("selfdriveActive", car.selfdriveActive)
    vmi.setBoolean("madsActive", car.madsActive)
    vmi.setBoolean("experimentalMode", car.experimentalMode)
    vmi.setBoolean("changingLane", car.changingLane)

    vmi.setNumber("odometer", car.odometer)
    vmi.setNumber("carBattery", stateOfCharge(car.fullPackEnergy, car.nominalEnergyRemaining, car.energyBuffer))
    vmi.setNumber("carBatteryTemp", (car.packTMin + car.packTMax) / 2f)
    vmi.setNumber("acTemp", car.acTemp)
    vmi.setNumber("regPowerBar", powerBarPercent(car.packVoltage, car.packCurrent, car.maxRegenPower, car.maxDischargePower))
    vmi.setNumber("phoneBattery", dashState.phoneBattery.toFloat())
}

private fun speedCamDistance(meters: Int, distFactor: Float): Float =
    if (meters < 0) -1f else meters * distFactor

private fun stateOfCharge(fullPack: Float, remaining: Float, buffer: Float): Float {
    if (fullPack <= 0f || remaining <= 0f || fullPack - buffer <= 0f) return 0f
    return ((remaining - buffer) / (fullPack - buffer) * 100f).coerceIn(0f, 100f)
}

// Signed percentage the Rive "Reg Charge" range mappers expect: positive is
// discharge relative to maxDischargePower, negative is regen relative to
// maxRegenPower. Matches the power bar in dash-apps/web-vanilla.
private fun powerBarPercent(voltage: Float, current: Float, maxRegen: Float, maxDischarge: Float): Float {
    if (voltage <= 0f || maxDischarge <= 0f || maxRegen <= 0f) return 0f
    val powerKw = voltage * current / 1000f
    return if (powerKw >= 0f) {
        min(powerKw / maxDischarge, 1f) * 100f
    } else {
        -min(abs(powerKw) / maxRegen, 1f) * 100f
    }
}
