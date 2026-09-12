package com.softwiredtech.dashkitconnect.can

import android.content.Context
import com.softwiredtech.dashkitconnect.CarState
import com.softwiredtech.dashkitconnect.DashKitBleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Decoded vehicle state straight from a DashKit: subscribes to the CAN stream
 * and emits a fresh [CarState] for every BLE notification.
 *
 * ```
 * val source = DashKitCarStateSource(context, manager)
 * source.start()
 * manager.connect()
 * source.carState.collect { state -> ... }
 * ```
 *
 * The decoder is built on a background thread after [start]; frames that
 * arrive before it is ready are dropped, which on a continuous CAN stream
 * costs at most a few hundred milliseconds of data.
 */
class DashKitCarStateSource(
    context: Context,
    manager: DashKitBleManager,
    private val profile: DashKitVehicleProfile = DashKitVehicleProfile.Tesla,
) {
    private val appContext = context.applicationContext

    private val _carState = MutableSharedFlow<CarState>(replay = 1)
    val carState: SharedFlow<CarState> = _carState.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var decoder: DashKitDecoder? = null
    private var running = false

    private val canSource = DashKitCanSource(manager) { frames ->
        val state = synchronized(lock) { decoder?.decode(frames) } ?: return@DashKitCanSource
        _carState.tryEmit(state)
    }

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        scope.launch {
            val created = DashKitDecoder.create(appContext, profile)
            synchronized(lock) {
                if (running && decoder == null) decoder = created else created.close()
            }
        }
        canSource.start()
    }

    fun stop() {
        canSource.stop()
        scope.coroutineContext.cancelChildren()
        synchronized(lock) {
            running = false
            decoder?.close()
            decoder = null
        }
    }
}
