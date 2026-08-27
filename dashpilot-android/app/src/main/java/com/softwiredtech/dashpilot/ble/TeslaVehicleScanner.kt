package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.security.MessageDigest

@SuppressLint("MissingPermission") // BLUETOOTH_SCAN is in the manifest and requested with the other BLE permissions
class TeslaVehicleScanner(private val adapter: BluetoothAdapter?) {

    companion object {
        private const val TAG = "TeslaScanner"

        private const val ROLE_SUFFIXES = "CRDP"
        private const val MIN_TAIL_LEN = 4
        private const val MAX_TAIL_LEN = 6

        private fun isVinChar(c: Char): Boolean =
            c in '0'..'9' || (c in 'A'..'Z' && c != 'I' && c != 'O' && c != 'Q')

        private fun isHex(c: Char): Boolean =
            c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        internal fun matchesVin(advertisedName: String, vin: String): Boolean {
            val upper = vin.uppercase()
            if (upper.length != TeslaClient.VIN_LEN || !upper.all(::isVinChar)) return false

            if (isModernTeslaName(advertisedName)) {
                val tail = advertisedName.removePrefix("Tesla ").uppercase()
                return upper.endsWith(tail)
            }
            if (isLegacyTeslaName(advertisedName)) {
                return advertisedName.dropLast(1).equals(legacyNamePrefix(upper), ignoreCase = true)
            }
            return false
        }

        private fun legacyNamePrefix(vin: String): String {
            val hash = MessageDigest.getInstance("SHA-1")
                .digest(vin.toByteArray(Charsets.US_ASCII))
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            return "S${hash.take(16)}"
        }

        private fun isLegacyTeslaName(name: String): Boolean {
            if (name.length != 18 || name[0] != 'S') return false
            for (i in 1..16) if (!isHex(name[i])) return false
            return name[17] in ROLE_SUFFIXES
        }

        private fun isModernTeslaName(name: String): Boolean {
            val tail = if (name.startsWith("Tesla ")) name.removePrefix("Tesla ") else return false
            if (tail.length !in MIN_TAIL_LEN..MAX_TAIL_LEN) return false
            return tail.all(::isVinChar)
        }

    }

    fun scan(vin: String): Flow<BluetoothDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE scanning unavailable")
            close(IllegalStateException("Bluetooth LE scanning unavailable"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertised = result.scanRecord?.deviceName ?: return
                if (matchesVin(advertised, vin)) trySend(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed: $errorCode")
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (e: Exception) {
            Log.w(TAG, "BLE scan start failed: ${e.message}")
            close(e)
            return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}
