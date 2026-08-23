package com.softwiredtech.dashpilot.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.security.MessageDigest

/**
 * A detected Tesla advertisement. [advertisedName] is the raw local name
 * ("Tesla <last6>" or legacy "S<16 hex><role>"); [rssi] is the measured signal
 * strength used to tell cars apart at a glance (the car you're standing next
 * to reads strongest). The phone cannot reconstruct a full VIN from either
 * name form — legacy is a one-way SHA-1 hash of the VIN, modern only carries
 * the last 6 characters — so the VIN is still collected from the user before
 * provisioning, then confirmed against the advert.
 */
data class TeslaVehicle(
    val advertisedName: String,
    val device: BluetoothDevice,
    val rssi: Int,
)

/**
 * Scans for Tesla vehicles advertising. Two modes:
 *
 *  - [scan] (VIN-derived): scans for a *specific* VIN using the two
 *    VIN-derived advert names and emits the first match.
 *  - [scanNearby]: scans for *any* Tesla-format advertisement (legacy or
 *    modern name forms) and emits each detected vehicle, so the user can pick
 *    their car from what is actually on air instead of typing a full VIN
 *    blind. The DashKit has no BLE observer, so the phone does discovery.
 *
 * Advert name formats:
 *  - legacy: "S" + first 16 hex chars of SHA1(VIN) + role letter ('C' here)
 *  - modern: "Tesla " + last 4..6 VIN characters (the firmware matched a 4..6
 *    tail; the scan filter uses the full-adverted scan record so we match the
 *    same tolerance here).
 */
@SuppressLint("MissingPermission") // BLUETOOTH_SCAN is in the manifest and requested with the other BLE permissions
class TeslaVehicleScanner(private val adapter: BluetoothAdapter?) {

    companion object {
        private const val TAG = "TeslaScanner"

        /** VIN charset (ISO 3779): A-Z minus I/O/Q plus digits. */
        private fun isVinChar(c: Char): Boolean =
            c in '0'..'9' || (c in 'A'..'Z' && c != 'I' && c != 'O' && c != 'Q')

        private fun isHex(c: Char): Boolean =
            c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        /** VIN-derived advert names used by Android scan filters. */
        internal fun derivedNames(vin: String): Set<String> {
            val upper = vin.uppercase()
            val sha1Hex = MessageDigest.getInstance("SHA-1")
                .digest(upper.toByteArray(Charsets.US_ASCII))
                .joinToString("") { "%02x".format(it) }
            return setOf("S" + sha1Hex.take(16) + "C", "Tesla " + upper.takeLast(6))
        }

        /**
         * True when [advertisedName] identifies [vin]. Modern cars may expose
         * only the last 4..6 VIN characters; legacy names compare the VIN hash
         * case-insensitively and ignore the advertised role suffix.
         */
        internal fun matchesVin(advertisedName: String, vin: String): Boolean {
            val upper = vin.uppercase()
            if (upper.length != TeslaClient.VIN_LEN || !upper.all(::isVinChar)) return false

            if (isModernTeslaName(advertisedName)) {
                val tail = advertisedName.removePrefix("Tesla ").uppercase()
                return upper.endsWith(tail)
            }
            if (isLegacyTeslaName(advertisedName)) {
                val expectedHashPrefix = derivedNames(upper).first { it.startsWith("S") }.dropLast(1)
                return advertisedName.dropLast(1).equals(expectedHashPrefix, ignoreCase = true)
            }
            return false
        }

        /** Legacy name: 'S' + 16 hex + role letter C/R/D/P (18 chars). */
        fun isLegacyTeslaName(name: String): Boolean {
            if (name.length != 18 || name[0] != 'S') return false
            for (i in 1..16) if (!isHex(name[i])) return false
            return name[17] in "CRDP"
        }

        /** Modern name: "Tesla " + 4..6 VIN-alphabet chars. */
        fun isModernTeslaName(name: String): Boolean {
            val tail = if (name.startsWith("Tesla ")) name.removePrefix("Tesla ") else return false
            if (tail.length !in 4..6) return false
            return tail.all(::isVinChar)
        }

        fun isTeslaName(name: String): Boolean =
            isLegacyTeslaName(name) || isModernTeslaName(name)
    }

    /** Emits each advertisement whose advertised local name matches the derived names for [vin]. */
    fun scan(vin: String): Flow<BluetoothDevice> = scanTeslaFilter(derivedNames(vin))

    /**
     * Emits every Tesla-format advertisement seen while scanning. Callers
     * dedupe by device address / collapse to a Map. Returns an empty flow (no
     * scan started) when Bluetooth LE scanning is unavailable.
     */
    fun scanNearby(): Flow<TeslaVehicle> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE scanning unavailable")
            close(IllegalStateException("Bluetooth LE scanning unavailable"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertised = result.scanRecord?.deviceName ?: return
                if (isTeslaName(advertised)) {
                    trySend(
                        TeslaVehicle(
                            advertisedName = advertised,
                            device = result.device,
                            rssi = result.rssi,
                        )
                    )
                }
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

    /** Emits each advertisement whose advertised local name matches [names] exactly. */
    private fun scanTeslaFilter(names: Set<String>): Flow<BluetoothDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth LE scanning unavailable"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Belt and braces: the ScanFilter already matches by name, but
                // some OEM stacks filter inconsistently, so verify here too.
                val advertised = result.scanRecord?.deviceName ?: return
                if (advertised in names) trySend(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed: $errorCode")
                close(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }

        val filters = names.map { ScanFilter.Builder().setDeviceName(it).build() }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}
