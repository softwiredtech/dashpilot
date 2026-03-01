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
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.ViewModelSource
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorkerOrNull
import app.rive.rememberViewModelInstance
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datasource.IDataSource
import com.softwiredtech.dashpilot.datasource.WebsocketDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@Composable
fun RiveDashView(
    dataSource: IDataSource,
    scope: CoroutineScope,
    onError: ((message: String) -> Unit)
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val errorState = remember { mutableStateOf<Throwable?>(null) }
        val riveWorker = rememberRiveWorkerOrNull(errorState)
        if (riveWorker == null) {
            onError(errorState.value.toString())
            return
        }
        val myFile: MutableState<RiveFile?> = remember { mutableStateOf(null) }

        val riveFile = rememberRiveFile(
            RiveFileSource.RawRes.from(R.raw.dashboard_test),
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
                        dataSource.incomingMessages.collect { message ->
                            vmi.setNumber("speed", message.world.ego_speed)
                            vmi.setNumber("speedLimit", message.world.fusedSpeedLimit)
                            vmi.setNumber("gear", message.world.gear)
                            vmi.setNumber("steeringAngle", message.world.ego_steering_angle)
                            vmi.setNumber("blinkerLeft", message.world.leftBlinker)
                            vmi.setNumber("blinkerRight", message.world.rightBlinker)
                            vmi.setNumber("leftBlindspot", message.world.leftBlindSpot)
                            vmi.setNumber("rightBlindspot", message.world.rightBlindSpot)
                            vmi.setNumber("stopDist", message.world.stopLineDist)
                            vmi.setNumber("trafficLightColor", message.world.trafficLightColor)
                            vmi.setNumber("trafficLightDist", message.world.stopLineDist)
                        }
                    }
                }
            }
        }
    }
}