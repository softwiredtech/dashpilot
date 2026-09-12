package com.softwiredtech.dashkitconnect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot scan that answers "is a DashKit advertising nearby, and is this
 * phone already bonded to it?" without opening a connection. Useful for
 * deciding at launch whether to auto-connect, run a pairing flow, or fall
 * back to another data source.
 */
object DashKitDiscovery {

    private const val TAG = "DashKitDiscovery"

    data class Result(val device: BluetoothDevice, val bonded: Boolean)

    /** Returns null if no DashKit was seen within [timeoutMs], scanning is
     * unavailable, or the scan failed. */
    @SuppressLint("MissingPermission")
    suspend fun find(context: Context, timeoutMs: Long = 3_000L): Result? =
        withTimeoutOrNull(timeoutMs) { scanOnce(context) }

    @SuppressLint("MissingPermission")
    private suspend fun scanOnce(context: Context): Result? =
        suspendCancellableCoroutine { cont ->
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.device.name ?: result.scanRecord?.deviceName
                    if (name != DashKitBleManager.DEVICE_NAME) return
                    val bonded = result.device.bondState == BluetoothDevice.BOND_BONDED
                    try { scanner.stopScan(this) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(Result(result.device, bonded))
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Discovery scan failed: $errorCode")
                    if (cont.isActive) cont.resume(null)
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            try {
                scanner.startScan(null, settings, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Discovery scan could not start: ${e.message}")
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try { scanner.stopScan(callback) } catch (_: Exception) {}
            }
        }
}
