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
import com.softwiredtech.dashpilot.datamodel.DashState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.core.net.toUri


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
            if (fileUri != null) {
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
                Rive(
                    riveFile.value,
                    viewModelInstance = vmi
                )
                myFile.value = riveFile.value
                vmi.setNumber("speed", 90f)

                LaunchedEffect(Unit) {
                    scope.launch {
                        dashStateFlow.collect { dashState ->
                            val message = dashState.carState
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
