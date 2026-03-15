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
import app.rive.ViewModelSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorkerOrNull
import app.rive.rememberViewModelInstance
import com.softwiredtech.dashpilot.datamodel.CarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch


@Composable
fun RiveDashView(
    assetName: String,
    carStateFlow: Flow<CarState>,
    scope: CoroutineScope,
    onError: ((message: String) -> Unit)
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

        val resId = remember(assetName) {
            context.resources.getIdentifier(assetName, "raw", context.packageName)
        }
        val riveFile = rememberRiveFile(
            RiveFileSource.RawRes.from(resId),
            riveWorker
        )

        when (riveFile) {
            is Result.Loading -> {
            }

            is Result.Error -> {
                onError(riveFile.throwable.toString())
            }

            is Result.Success -> {
                val file = riveFile.value
                val vmiSource = ViewModelSource.Named("MainViewModel").defaultInstance()
                val vmi = rememberViewModelInstance(file, vmiSource)
                Rive(
                    riveFile.value,
                    viewModelInstance = vmi
                )
                myFile.value = riveFile.value
                vmi.setNumber("speed", 90f)

                LaunchedEffect(Unit) {
                    scope.launch {
                        carStateFlow.collect { message ->
                            vmi.setNumber("speed", message.egoSpeed)
                            vmi.setNumber("speedLimit", message.fusedSpeedLimit)
                            vmi.setNumber("gear", message.gear)
                            vmi.setNumber("steeringAngle", message.egoSteeringAngle)
                            vmi.setNumber("blinkerLeft", message.leftBlinker)
                            vmi.setNumber("blinkerRight", message.rightBlinker)
                            vmi.setNumber("leftBlindspot", message.leftBlindSpot)
                            vmi.setNumber("rightBlindspot", message.rightBlindSpot)
                            vmi.setNumber("stopDist", message.stopLineDist)
                            vmi.setNumber("trafficLightColor", message.trafficLightColor)
                            vmi.setNumber("trafficLightDist", message.stopLineDist)
                        }
                    }
                }
            }
        }
    }
}
