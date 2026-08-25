package com.softwiredtech.dashpilot.ui.tesla

import android.bluetooth.BluetoothManager
import android.content.Context
import com.softwiredtech.dashpilot.ble.TeslaClient
import com.softwiredtech.dashpilot.ble.TeslaFaultDetail
import com.softwiredtech.dashpilot.ble.TeslaLinkState
import com.softwiredtech.dashpilot.ble.TeslaStatus
import com.softwiredtech.dashpilot.ble.TeslaVehicleScanner
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.vehicle.VehicleVinState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun maskVinTail(vin: String?): String? =
    vin?.takeLast(4)?.let { "••••$it" }

fun interface TeslaMatchScanner {
    fun scanMatches(vin: String): Flow<String>
}

enum class TeslaEnrollmentErrorReason {
    FirmwareUnsupported,
    VinUnavailable,
    VehicleNotFound,
    NotAcknowledged,
    TapTimeout,
    Rejected,
    Protocol,
    Persist,
    Generic,
}

internal fun faultReason(detail: TeslaFaultDetail): TeslaEnrollmentErrorReason = when (detail) {
    TeslaFaultDetail.TapTimeout -> TeslaEnrollmentErrorReason.TapTimeout
    TeslaFaultDetail.Rejected -> TeslaEnrollmentErrorReason.Rejected
    TeslaFaultDetail.Protocol -> TeslaEnrollmentErrorReason.Protocol
    TeslaFaultDetail.Persist -> TeslaEnrollmentErrorReason.Persist
    TeslaFaultDetail.None -> TeslaEnrollmentErrorReason.Generic
}

sealed interface TeslaEnrollmentState {
    data object CheckingFirmware : TeslaEnrollmentState
    data object WaitingForVin : TeslaEnrollmentState
    data class FindingVehicle(val maskedVin: String?) : TeslaEnrollmentState
    data class Provisioning(val maskedVin: String?) : TeslaEnrollmentState

    data class ReadyToConnect(val maskedVin: String?) : TeslaEnrollmentState
    data class Connecting(val maskedVin: String?) : TeslaEnrollmentState
    data class WaitingForKeyCard(val carReady: Boolean, val maskedVin: String?) : TeslaEnrollmentState
    data object Success : TeslaEnrollmentState
    data class Error(val reason: TeslaEnrollmentErrorReason, val maskedVin: String?) : TeslaEnrollmentState
}

class TeslaEnrollmentController(
    private val scope: CoroutineScope,
    private val vinState: StateFlow<VehicleVinState>,
    private val status: StateFlow<TeslaStatus>,
    private val matchScanner: TeslaMatchScanner,
    private val hasTeslaService: () -> Boolean,
    private val provision: (vin: String, mac: String) -> Boolean,
    private val startEnrollment: () -> Boolean,
    private val cancelPairing: () -> Unit,
) {

    companion object {
        const val VIN_TIMEOUT_MS = 20_000L
        const val SCAN_TIMEOUT_MS = 20_000L
        const val PROVISION_ACK_TIMEOUT_MS = 8_000L
        const val CAPABILITY_TIMEOUT_MS = 6_000L
        const val CAPABILITY_POLL_MS = 250L
        const val PROVISION_DISPATCH_RETRIES = 3
        const val PROVISION_RETRY_DELAY_MS = 750L
        fun create(
            scope: CoroutineScope,
            context: Context,
            manager: DashKitBleManager?,
            vinState: StateFlow<VehicleVinState>,
            status: StateFlow<TeslaStatus>,
        ): TeslaEnrollmentController {
            @Suppress("MissingPermission") // BLUETOOTH_SCAN requested with the other BLE permissions
            val adapter = runCatching {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            }.getOrNull()
            val scanner = TeslaVehicleScanner(adapter)
            return TeslaEnrollmentController(
                scope = scope,
                vinState = vinState,
                status = status,
                matchScanner = TeslaMatchScanner { vin ->
                    scanner.scan(vin).map { it.address }
                },
                hasTeslaService = {
                    manager?.gatt?.getService(TeslaClient.SERVICE_UUID)
                        ?.getCharacteristic(TeslaClient.COMMAND_CHAR_UUID) != null
                },
                provision = { vin, mac ->
                    manager?.let { TeslaClient.sendProvision(it, vin, mac) } ?: false
                },
                startEnrollment = { manager?.let { TeslaClient.sendStart(it) } ?: false },
                cancelPairing = { manager?.let { TeslaClient.sendCancel(it) } },
            )
        }
    }

    private val _state = MutableStateFlow<TeslaEnrollmentState>(TeslaEnrollmentState.CheckingFirmware)
    val state: StateFlow<TeslaEnrollmentState> = _state.asStateFlow()

    private fun set(next: TeslaEnrollmentState) {
        _state.value = next
    }

    private var pipeline: Job? = null
    private var observer: Job? = null

    private fun vin(): String? = (vinState.value as? VehicleVinState.Available)?.vin

    fun begin() {
        if (observer?.isActive == true) return
        observer = scope.launch {
            status.collect { onStatus(it) }
        }
        runPipeline()
    }

    fun stop() {
        observer?.cancel()
        observer = null
        pipeline?.cancel()
        pipeline = null
    }

    fun retry() {
        val current = _state.value
        if (current !is TeslaEnrollmentState.Error) return
        when (current.reason) {
            TeslaEnrollmentErrorReason.TapTimeout,
            TeslaEnrollmentErrorReason.Rejected,
            TeslaEnrollmentErrorReason.Protocol,
            TeslaEnrollmentErrorReason.Persist,
            TeslaEnrollmentErrorReason.Generic,
            -> {
                if (startEnrollment()) {
                    set(TeslaEnrollmentState.Connecting(current.maskedVin))
                } else {
                    set(error(TeslaEnrollmentErrorReason.NotAcknowledged))
                }
            }
            TeslaEnrollmentErrorReason.FirmwareUnsupported,
            TeslaEnrollmentErrorReason.VinUnavailable,
            TeslaEnrollmentErrorReason.VehicleNotFound,
            TeslaEnrollmentErrorReason.NotAcknowledged,
            -> runPipeline()
        }
    }

    fun connect() {
        val current = _state.value
        if (current !is TeslaEnrollmentState.ReadyToConnect) return
        if (startEnrollment()) {
            set(TeslaEnrollmentState.Connecting(current.maskedVin))
        } else {
            set(error(TeslaEnrollmentErrorReason.NotAcknowledged))
        }
    }

    fun cancelPairingWindow() = cancelPairing()

    private fun terminalStateFor(status: TeslaStatus): TeslaEnrollmentState? =
        when (status.linkState) {
            TeslaLinkState.Staged ->
                TeslaEnrollmentState.ReadyToConnect(maskVinTail(vin()))
            TeslaLinkState.Connecting ->
                TeslaEnrollmentState.Connecting(maskVinTail(vin()))
            TeslaLinkState.PairingWindow ->
                waitingForKeyCard(status)
            TeslaLinkState.EnrolledNotConnected, TeslaLinkState.EnrolledConnected ->
                TeslaEnrollmentState.Success
            TeslaLinkState.EnrollmentFault ->
                error(faultReason(status.faultDetail))
            TeslaLinkState.NeverEnrolled, TeslaLinkState.Unknown -> null
        }

    private fun runPipeline() {
        pipeline?.cancel()
        pipeline = scope.launch {
            set(TeslaEnrollmentState.CheckingFirmware)

            // Service discovery may still be finishing when this screen opens.
            val serviceReady = withTimeoutOrNull(CAPABILITY_TIMEOUT_MS) {
                while (!hasTeslaService()) delay(CAPABILITY_POLL_MS)
                true
            } ?: false
            if (!serviceReady) {
                set(error(TeslaEnrollmentErrorReason.FirmwareUnsupported))
                return@launch
            }

            terminalStateFor(status.value)?.let { route ->
                set(route)
                return@launch
            }

            set(TeslaEnrollmentState.WaitingForVin)
            val vin = withTimeoutOrNull(VIN_TIMEOUT_MS) {
                vinState.first { it is VehicleVinState.Available }
            }?.let { (it as VehicleVinState.Available).vin }

            terminalStateFor(status.value)?.let { route ->
                set(route)
                return@launch
            }
            if (vin == null) {
                set(error(TeslaEnrollmentErrorReason.VinUnavailable))
                return@launch
            }

            set(TeslaEnrollmentState.FindingVehicle(maskVinTail(vin)))
            val mac = try {
                withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    matchScanner.scanMatches(vin).firstOrNull()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            terminalStateFor(status.value)?.let { route ->
                set(route)
                return@launch
            }
            if (mac == null) {
                set(error(TeslaEnrollmentErrorReason.VehicleNotFound))
                return@launch
            }

            set(TeslaEnrollmentState.Provisioning(maskVinTail(vin)))
            var dispatched = false
            for (attempt in 0 until PROVISION_DISPATCH_RETRIES) {
                val ok = runCatching { provision(vin, mac) }.getOrDefault(false)
                if (ok) {
                    dispatched = true
                    break
                }
                if (attempt < PROVISION_DISPATCH_RETRIES - 1) delay(PROVISION_RETRY_DELAY_MS)
            }
            if (!dispatched) {
                set(error(TeslaEnrollmentErrorReason.NotAcknowledged))
                return@launch
            }

            val verdict = withTimeoutOrNull(PROVISION_ACK_TIMEOUT_MS) {
                status.first {
                    it.linkState == TeslaLinkState.Staged ||
                        it.linkState == TeslaLinkState.EnrollmentFault ||
                        it.linkState == TeslaLinkState.EnrolledNotConnected ||
                        it.linkState == TeslaLinkState.EnrolledConnected
                }.linkState
            }
            set(
                when (verdict) {
                    TeslaLinkState.Staged -> TeslaEnrollmentState.ReadyToConnect(maskVinTail(vin))
                    TeslaLinkState.EnrollmentFault ->
                        error(faultReason(status.value.faultDetail))
                    TeslaLinkState.EnrolledNotConnected, TeslaLinkState.EnrolledConnected ->
                        TeslaEnrollmentState.Success
                    else -> error(TeslaEnrollmentErrorReason.NotAcknowledged)
                }
            )
        }
    }

    private fun onStatus(s: TeslaStatus) {
        val current = _state.value
        when (s.linkState) {
            TeslaLinkState.NeverEnrolled, TeslaLinkState.Unknown -> Unit

            TeslaLinkState.Staged ->
                if (current !is TeslaEnrollmentState.Provisioning &&
                    current !is TeslaEnrollmentState.ReadyToConnect
                ) {
                    pipeline?.cancel()
                    set(TeslaEnrollmentState.ReadyToConnect(maskVinTail(vin())))
                }

            TeslaLinkState.Connecting, TeslaLinkState.PairingWindow -> {
                if (current !is TeslaEnrollmentState.Provisioning &&
                    current !is TeslaEnrollmentState.CheckingFirmware &&
                    current !is TeslaEnrollmentState.WaitingForVin &&
                    current !is TeslaEnrollmentState.FindingVehicle
                ) {
                    set(terminalStateFor(s)!!)
                }
            }

            TeslaLinkState.EnrolledNotConnected, TeslaLinkState.EnrolledConnected -> {
                pipeline?.cancel()
                set(TeslaEnrollmentState.Success)
            }
            TeslaLinkState.EnrollmentFault -> {
                pipeline?.cancel()
                set(error(faultReason(s.faultDetail)))
            }
        }
    }

    private fun waitingForKeyCard(s: TeslaStatus) = TeslaEnrollmentState.WaitingForKeyCard(
        carReady = s.flags and 0x01 != 0,
        maskedVin = maskVinTail(vin()),
    )

    private fun error(reason: TeslaEnrollmentErrorReason) =
        TeslaEnrollmentState.Error(reason, maskVinTail(vin()))
}
