package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import app.rive.ExperimentalDeferredRendering
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.ViewModelSource
import app.rive.rememberDeferredRiveWorker
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorkerOrNull
import app.rive.rememberViewModelInstance
import app.rive.runtime.kotlin.core.ViewModel.PropertyDataType
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datamodel.dash.DashColor
import com.softwiredtech.dashpilot.datamodel.dash.dashValues
import kotlinx.coroutines.flow.Flow

private const val DASH_VIEW_MODEL = "DashViewModel"
private const val ARTBOARD_HEIGHT = 1080f

@OptIn(ExperimentalDeferredRendering::class)
@Composable
fun RiveDashView(
    assetName: String,
    dashStateFlow: Flow<DashState>,
    onError: ((message: String) -> Unit),
    fileUri: String? = null,
    useGpuCanvas: Boolean = false
) {
    val systemDarkMode = isSystemInDarkTheme()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (systemDarkMode) Color.Black else Color.White)
    ) {
        val fit = Fit.Layout(constraints.maxHeight / ARTBOARD_HEIGHT)
        val context = LocalContext.current
        // GPU Canvas content (WGSL shaders over a script-drawn canvas) only renders on a deferred
        // worker. It is opt-in from Developer settings; every other case keeps the standard worker.
        val errorState = remember { mutableStateOf<Throwable?>(null) }
        val riveWorker = if (useGpuCanvas) rememberDeferredRiveWorker() else rememberRiveWorkerOrNull(errorState)
        if (riveWorker == null) {
            onError(errorState.value.toString())
            return@BoxWithConstraints
        }

        val riveSource = remember(assetName, fileUri) {
            if (!fileUri.isNullOrEmpty()) {
                try {
                    val bytes = context.contentResolver.openInputStream(fileUri.toUri())?.use { it.readBytes() }
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
            return@BoxWithConstraints
        }

        when (val riveFile = rememberRiveFile(riveSource, riveWorker)) {
            is Result.Loading -> {}
            is Result.Error -> onError(riveFile.throwable.toString())
            is Result.Success -> {
                val file = riveFile.value
                val properties by produceState<Map<String, PropertyDataType>?>(null, file) {
                    value = runCatching {
                        if (DASH_VIEW_MODEL in file.getViewModelNames()) {
                            file.getViewModelProperties(DASH_VIEW_MODEL).associate { it.name to it.type }
                        } else {
                            emptyMap()
                        }
                    }.getOrDefault(emptyMap())
                }
                when {
                    properties == null -> {}
                    properties!!.isEmpty() -> {
                        onError("No $DASH_VIEW_MODEL in this file")
                        Rive(file, fit = fit)
                    }
                    else -> BoundRive(file, properties!!, dashStateFlow, fit, systemDarkMode)
                }
            }
        }
    }
}

@Composable
private fun BoundRive(
    file: RiveFile,
    properties: Map<String, PropertyDataType>,
    dashStateFlow: Flow<DashState>,
    fit: Fit,
    systemDarkMode: Boolean,
) {
    val vmi = rememberViewModelInstance(file, ViewModelSource.Named(DASH_VIEW_MODEL).defaultInstance())
    Rive(file, viewModelInstance = vmi, fit = fit)

    LaunchedEffect(vmi, properties, dashStateFlow, systemDarkMode) {
        // Appearance must update even when no vehicle telemetry is arriving.
        if (properties["darkMode"] == PropertyDataType.BOOLEAN) {
            vmi.setBoolean("darkMode", systemDarkMode)
        }
        dashStateFlow.collect { state ->
            applyRiveDashValues(properties, state.dashValues(systemDarkMode),
                vmi::setNumber, vmi::setBoolean, vmi::setString, vmi::setColor)
        }
    }
}

// Keep typed dispatch independent of the native Rive worker so it can be
// checked with successive telemetry snapshots as well as newly mounted files.
internal fun applyRiveDashValues(
    properties: Map<String, PropertyDataType>,
    values: Map<String, Any>,
    setNumber: (String, Float) -> Unit,
    setBoolean: (String, Boolean) -> Unit,
    setString: (String, String) -> Unit,
    setColor: (String, Int) -> Unit,
) {
    for ((name, value) in values) {
        when (properties[name]) {
            PropertyDataType.NUMBER -> (value as? Float)?.let { setNumber(name, it) }
            PropertyDataType.BOOLEAN -> (value as? Boolean)?.let { setBoolean(name, it) }
            PropertyDataType.STRING -> setString(name, value.toString())
            PropertyDataType.COLOR -> (value as? DashColor)?.let { setColor(name, it.argb) }
            else -> {}
        }
    }
}
