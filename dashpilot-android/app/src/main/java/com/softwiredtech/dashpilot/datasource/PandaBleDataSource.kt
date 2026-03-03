package com.softwiredtech.dashpilot.datasource

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.softwiredtech.dashpilot.datamodel.CarState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import com.softwiredtech.dashpilot.vehicle.CanFrameDecoder

@SuppressLint("MissingPermission")
class PandaBleDataSource(
    private val context: Context,
    private val decoder: CanFrameDecoder
) : IDataSource {

    companion object {
        private const val TAG = "PandaBleDataSource"
        private const val DEVICE_NAME = "PandaCAN"
        private val SERVICE_UUID = UUID.fromString("CADA0000-CA00-B1E0-B0D6-C000AA0100A1")
        private val CHAR_UUID = UUID.fromString("CADA0001-CA00-B1E0-B0D6-C000AA0100A1")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val _incoming = MutableSharedFlow<CarState>(replay = 1)
    override val incomingMessages: Flow<CarState> = _incoming

    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var currentState = CarState()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
            if (name == DEVICE_NAME) {
                Log.d(TAG, "Found PandaCAN: ${result.device.address}")
                stopScan()
                result.device.connectGatt(context, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server")
                gatt = g
                // Clear Android's GATT cache to avoid stale service lists
                try {
                    val refresh = g.javaClass.getMethod("refresh")
                    val result = refresh.invoke(g) as Boolean
                    Log.d(TAG, "GATT cache refresh: $result")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear GATT cache: ${e.message}")
                }
                // Delay service discovery to let cache clear take effect
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    g.discoverServices()
                    Log.d(TAG, "Service discovery started")
                }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
                gatt = null
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }
            Log.d(TAG, "Discovered ${g.services.size} services:")
            for (s in g.services) {
                Log.d(TAG, "  service: ${s.uuid}")
                for (c in s.characteristics) {
                    Log.d(TAG, "    char: ${c.uuid} props=${c.properties}")
                }
            }
            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Panda BLE service not found (looking for $SERVICE_UUID)")
                return
            }
            val characteristic = service.getCharacteristic(CHAR_UUID)
            if (characteristic == null) {
                Log.e(TAG, "Panda BLE characteristic not found")
                return
            }
            g.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
            }
            Log.d(TAG, "Subscribed to CAN notifications")
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            parseAndEmit(value)
        }
    }

    override fun connect(address: String) {
        startScan()
    }

    override fun disconnect() {
        stopScan()
        gatt?.close()
        gatt = null
    }

    private fun startScan() {
        val adapter = android.bluetooth.BluetoothManager::class.java
            .let { context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager }
            .adapter ?: run {
            Log.e(TAG, "Bluetooth not available")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available")
            return
        }
        // Scan with no filter first to debug — log everything Android can see.
        // We'll match by name ("PandaCAN") in the callback once we confirm visibility.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            Log.d(TAG, "BLE scan started (unfiltered, logging all devices)")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan failed — missing permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan failed: ${e.message}")
        }
    }

    private fun stopScan() {
        if (!scanning) return
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    private fun parseAndEmit(payload: ByteArray) {
        if (payload.isEmpty()) return
        val count = payload[0].toInt() and 0xFF
        var offset = 1
        for (i in 0 until count) {
            if (offset >= payload.size) break
            val bus = payload[offset].toInt() and 0xFF
            offset += 1
            if (offset + 4 > payload.size) break
            val addr = ByteBuffer.wrap(payload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            offset += 4
            if (offset >= payload.size) break
            val len = payload[offset].toInt() and 0xFF
            offset += 1
            if (offset + len > payload.size) break
            val data = payload.copyOfRange(offset, offset + len)
            offset += len

            currentState = decoder.decodeFrame(bus, addr, data)
        }
        _incoming.tryEmit(currentState)
    }

}
