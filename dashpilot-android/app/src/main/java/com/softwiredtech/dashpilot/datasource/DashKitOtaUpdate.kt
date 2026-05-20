package com.softwiredtech.dashpilot.datasource

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

sealed class OtaState {
    object Idle : OtaState()
    object Connecting : OtaState()
    data class Uploading(val progress: Float) : OtaState()
    object Rebooting : OtaState()
    data class Error(val message: String) : OtaState()
}

@SuppressLint("MissingPermission")
class DashKitOtaUpdate(
    private val manager: DashKitBleManager,
    private val context: Context
) : GattListener {

    companion object {
        private const val TAG = "DashKitOta"
        private val OTA_SERVICE_UUID = UUID.fromString("CADA0100-CA00-B1E0-B0D6-C000AA0100A1")
        private val OTA_CTRL_UUID = UUID.fromString("CADA0101-CA00-B1E0-B0D6-C000AA0100A1")
        private val OTA_DATA_UUID = UUID.fromString("CADA0102-CA00-B1E0-B0D6-C000AA0100A1")
        private val OTA_STATUS_UUID = UUID.fromString("CADA0103-CA00-B1E0-B0D6-C000AA0100A1")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val FIRMWARE_ASSET = "dashkit-firmware.bin"
    }

    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state: StateFlow<OtaState> = _state

    private var firmware: ByteArray? = null
    private var firmwareOffset = 0
    private var ctrlChar: BluetoothGattCharacteristic? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var statusChar: BluetoothGattCharacteristic? = null

    fun start() {
        val fw = loadFirmware()
        if (fw == null || fw.isEmpty()) {
            _state.value = OtaState.Error("Firmware file not found or empty")
            return
        }
        firmware = fw
        firmwareOffset = 0

        manager.addGattListener(this)

        // If already connected, use existing GATT directly
        val g = manager.gatt
        if (g != null && manager.connectionState.value == ConnectionStatus.Connected) {
            setupOtaService(g)
        } else {
            _state.value = OtaState.Connecting
            manager.connect()
        }
    }

    fun cancel() {
        manager.removeGattListener(this)
        firmware = null
        ctrlChar = null
        dataChar = null
        statusChar = null
        _state.value = OtaState.Idle
    }

    override fun onServicesReady(gatt: BluetoothGatt) {
        setupOtaService(gatt)
    }

    private fun setupOtaService(g: BluetoothGatt) {
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
        Log.d(TAG, "OTA service ready, subscribed to status")
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        // Only handle OTA status descriptor
        if (descriptor.characteristic?.uuid != OTA_STATUS_UUID) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            _state.value = OtaState.Error("Failed to enable OTA notifications")
            return
        }
        sendBeginCommand(gatt)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (characteristic.uuid != OTA_STATUS_UUID) return
        handleStatusNotification(value)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (characteristic.uuid != OTA_CTRL_UUID && characteristic.uuid != OTA_DATA_UUID) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            _state.value = OtaState.Error("Write failed (status $status)")
            return
        }
        sendNextChunk(gatt)
    }

    override fun onDisconnected() {
        val currentState = _state.value
        if (currentState !is OtaState.Rebooting && currentState !is OtaState.Idle) {
            _state.value = OtaState.Error("Disconnected unexpectedly")
        }
        firmware = null
        ctrlChar = null
        dataChar = null
        statusChar = null
    }

    private fun loadFirmware(): ByteArray? {
        return try {
            context.assets.open(FIRMWARE_ASSET).use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load firmware: ${e.message}")
            null
        }
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

        val chunkSize = minOf(manager.mtu - 3, fw.size - firmwareOffset)
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
                manager.removeGattListener(this)
                firmware = null
            }
            0xFF -> {
                val errCode = if (value.size > 1) value[1].toInt() and 0xFF else 0
                Log.e(TAG, "OTA error from device: 0x${errCode.toString(16)}")
                _state.value = OtaState.Error("Device reported error (0x${errCode.toString(16)})")
                manager.removeGattListener(this)
                firmware = null
            }
        }
    }
}
