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
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

sealed class OtaState {
    object Idle : OtaState()
    object Scanning : OtaState()
    object Connecting : OtaState()
    data class Uploading(val progress: Float) : OtaState()
    object Rebooting : OtaState()
    data class Error(val message: String) : OtaState()
}

@SuppressLint("MissingPermission")
class DashKitOtaUpdate(private val context: Context) {

    companion object {
        private const val TAG = "DashKitOta"
        private const val DEVICE_NAME = "DashKit"
        private val OTA_SERVICE_UUID = UUID.fromString("CADA0100-CA00-B1E0-B0D6-C000AA0100A1")
        private val OTA_CTRL_UUID = UUID.fromString("CADA0101-CA00-B1E0-B0D6-C000AA0100A1")
        private val OTA_DATA_UUID = UUID.fromString("CADA0102-CA00-B1E0-B0D6-C000AA0100A1")
        private val OTA_STATUS_UUID = UUID.fromString("CADA0103-CA00-B1E0-B0D6-C000AA0100A1")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val FIRMWARE_ASSET = "dashkit-firmware.bin"
    }

    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state: StateFlow<OtaState> = _state

    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var firmware: ByteArray? = null
    private var firmwareOffset = 0
    private var ctrlChar: BluetoothGattCharacteristic? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private var mtu = 23

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
            if (name == DEVICE_NAME) {
                Log.d(TAG, "Found DashKit: ${result.device.address}")
                stopScan()
                _state.value = OtaState.Connecting
                result.device.connectGatt(
                    context, false, gattCallback,
                    android.bluetooth.BluetoothDevice.TRANSPORT_LE
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
            _state.value = OtaState.Error("BLE scan failed (error $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT")
                gatt = g
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                try {
                    val refresh = g.javaClass.getMethod("refresh")
                    refresh.invoke(g)
                } catch (_: Exception) {}
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    g.requestMtu(512)
                }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT")
                val currentState = _state.value
                if (currentState !is OtaState.Rebooting && currentState !is OtaState.Idle) {
                    _state.value = OtaState.Error("Disconnected unexpectedly")
                }
                gatt = null
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtu = newMtu
                Log.d(TAG, "MTU changed to $newMtu")
            }
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = OtaState.Error("Service discovery failed")
                return
            }

            val service = g.getService(OTA_SERVICE_UUID)
            if (service == null) {
                _state.value = OtaState.Error("OTA service not found on device")
                return
            }

            ctrlChar = service.getCharacteristic(OTA_CTRL_UUID)
            dataChar = service.getCharacteristic(OTA_DATA_UUID)
            statusChar = service.getCharacteristic(OTA_STATUS_UUID)

            if (ctrlChar == null || dataChar == null || statusChar == null) {
                _state.value = OtaState.Error("OTA characteristics not found")
                return
            }

            g.setCharacteristicNotification(statusChar, true)
            val descriptor = statusChar!!.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            }
            Log.d(TAG, "OTA service discovered, subscribed to status")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = OtaState.Error("Failed to enable notifications")
                return
            }
            sendBeginCommand(g)
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleStatusNotification(value)
        }

        // API < 33
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleStatusNotification(value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = OtaState.Error("Write failed (status $status)")
                return
            }
            // After Begin or a data chunk is acknowledged, send the next chunk
            if (characteristic.uuid == OTA_CTRL_UUID || characteristic.uuid == OTA_DATA_UUID) {
                sendNextChunk(g)
            }
        }
    }

    fun start() {
        val fw = loadFirmware()
        if (fw == null || fw.isEmpty()) {
            _state.value = OtaState.Error("Firmware file not found or empty")
            return
        }
        firmware = fw
        firmwareOffset = 0
        _state.value = OtaState.Scanning
        startScan()
    }

    fun cancel() {
        stopScan()
        gatt?.close()
        gatt = null
        firmware = null
        _state.value = OtaState.Idle
    }

    private fun loadFirmware(): ByteArray? {
        return try {
            context.assets.open(FIRMWARE_ASSET).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load firmware: ${e.message}")
            null
        }
    }

    private fun startScan() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
            .adapter ?: run {
            _state.value = OtaState.Error("Bluetooth not available")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _state.value = OtaState.Error("BLE scanner not available")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
            scanning = true
        } catch (e: Exception) {
            _state.value = OtaState.Error("Scan failed: ${e.message}")
        }
    }

    private fun stopScan() {
        if (!scanning) return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        scanning = false
    }

    private fun sendBeginCommand(g: BluetoothGatt) {
        val fw = firmware ?: return
        val size = fw.size
        val cmd = byteArrayOf(
            0x01,
            (size and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 24) and 0xFF).toByte()
        )
        Log.d(TAG, "Sending OTA Begin: ${fw.size} bytes")
        _state.value = OtaState.Uploading(0f)
        firmwareOffset = 0

        val ctrl = ctrlChar ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ctrl, cmd, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ctrl.value = cmd
            ctrl.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ctrl)
        }
    }

    private fun sendNextChunk(g: BluetoothGatt) {
        val fw = firmware ?: return
        if (firmwareOffset >= fw.size) return

        val chunkSize = minOf(mtu - 3, fw.size - firmwareOffset)
        val chunk = fw.copyOfRange(firmwareOffset, firmwareOffset + chunkSize)
        firmwareOffset += chunkSize

        val progress = firmwareOffset.toFloat() / fw.size.toFloat()
        _state.value = OtaState.Uploading(progress)

        val data = dataChar ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(data, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            data.value = chunk
            data.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(data)
        }
    }

    private fun handleStatusNotification(value: ByteArray) {
        if (value.isEmpty()) return
        when (value[0].toInt() and 0xFF) {
            0x01 -> {
                // Progress ack from device (informational)
                if (value.size >= 5) {
                    val received = (value[1].toInt() and 0xFF) or
                            ((value[2].toInt() and 0xFF) shl 8) or
                            ((value[3].toInt() and 0xFF) shl 16) or
                            ((value[4].toInt() and 0xFF) shl 24)
                    Log.d(TAG, "Device ack: $received bytes received")
                }
            }
            0x02 -> {
                Log.d(TAG, "OTA complete, device rebooting")
                _state.value = OtaState.Rebooting
                gatt?.close()
                gatt = null
                firmware = null
            }
            0xFF -> {
                val errCode = if (value.size > 1) value[1].toInt() and 0xFF else 0
                Log.e(TAG, "OTA error from device: 0x${errCode.toString(16)}")
                _state.value = OtaState.Error("Device reported error (0x${errCode.toString(16)})")
                gatt?.close()
                gatt = null
                firmware = null
            }
        }
    }
}
