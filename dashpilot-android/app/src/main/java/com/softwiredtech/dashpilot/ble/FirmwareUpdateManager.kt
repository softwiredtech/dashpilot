package com.softwiredtech.dashpilot.ble

import android.content.Context
import android.util.Log
import com.softwiredtech.dashpilot.api.FirmwareManifest
import com.softwiredtech.dashpilot.api.FirmwareRelease
import com.softwiredtech.dashpilot.api.FirmwareUpdateRepository
import com.softwiredtech.dashpilot.api.DevelopmentFirmwareRepository
import com.softwiredtech.dashpilot.api.VerifiedPrFirmware
import com.softwiredtech.dashpilot.datamodel.dash.getCarModel
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.util.SemVer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import com.softwiredtech.dashpilot.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Orchestrates the end-to-end firmware update flow:
 *  1. read the installed version over BLE (CADA0005),
 *  2. fetch the release manifest from the server,
 *  3. SemVer-compare to decide if an update is available,
 *  4. download the binary (with progress),
 *  5. hand the bytes to the existing [DashKitOtaUpdate] BLE upload path.
 *
 * A single instance should be created per connected [DashKitBleManager] and
 * disposed when the screen leaves composition.
 */
class FirmwareUpdateManager(
    private val manager: DashKitBleManager
) {
    companion object {
        private const val TAG = "FwUpdateManager"
        private const val PREFS = "dashkit_firmware"
        private const val KEY_LOCAL = "local_firmware_installed"
        private const val ESP_IMAGE_MAGIC: Byte = 0xE9.toByte()
    }

    sealed class Check {
        object Idle : Check()
        object Checking : Check()
        object UpToDate : Check()
        data class Available(val manifest: FirmwareManifest) : Check()
        data class Error(val message: String) : Check()
    }

    private val ota = DashKitOtaUpdate(manager)
    private val versionReader = DashKitFirmwareVersion(manager)

    val otaState: StateFlow<OtaState> = ota.state
    val installedVersion: StateFlow<String?> = versionReader.version

    private val _check = MutableStateFlow<Check>(Check.Idle)
    val check: StateFlow<Check> = _check

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val prefs = manager.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _localFirmware = MutableStateFlow(prefs.getBoolean(KEY_LOCAL, false))
    val localFirmware: StateFlow<Boolean> = _localFirmware
    private var uploadingLocal = false
    @Volatile private var installGeneration = 0
    private val busy: Boolean get() = _downloadProgress.value != null ||
        otaState.value is OtaState.Connecting || otaState.value is OtaState.Uploading ||
        otaState.value is OtaState.Rebooting

    init {
        ota.onUploaded = {
            _localFirmware.value = uploadingLocal
            prefs.edit().putBoolean(KEY_LOCAL, uploadingLocal).apply()
        }
    }

    /** Begin reading the installed firmware version over BLE. */
    fun start() {
        versionReader.read()
    }

    /**
     * Fetch the manifest and compare with the installed version. Reads the
     * installed version first (waiting briefly if it hasn't arrived yet).
     */
    suspend fun checkForUpdate() {
        if (busy || _check.value is Check.Checking) return
        _check.value = Check.Checking
        val manifest = FirmwareUpdateRepository.fetchManifest(getCarModel(manager.context))
        if (manifest == null) {
            _check.value = Check.Error(manager.context.getString(R.string.fw_error_unreachable))
            return
        }
        val current = installedVersion.value ?: versionReader.await()
        if (current == null) {
            Log.w(TAG, "Installed version unknown; offering update anyway")
            _check.value = Check.Available(manifest)
            return
        }
        _check.value = if (_localFirmware.value || SemVer.isNewer(manifest.version, current)) {
            Check.Available(manifest)
        } else {
            Check.UpToDate
        }
    }

    /**
     * Download the firmware described by [manifest], verify its SHA-256 if the
     * manifest provides one, then start the BLE upload. Progress is reported
     * via [downloadProgress] during download and [otaState] during upload.
     */
    suspend fun install(manifest: FirmwareManifest, custom: Boolean = false) {
        if (busy) return
        val generation = ++installGeneration
        try {
            _downloadProgress.value = 0f
            val bytes = FirmwareUpdateRepository.downloadBinary(manifest.url) { p ->
                if (generation == installGeneration) _downloadProgress.value = p
            }
            currentCoroutineContext().ensureActive()
            if (generation != installGeneration) return

            val expected = manifest.sha256?.trim()?.lowercase()
            if (!expected.isNullOrEmpty()) {
                val actual = sha256Hex(bytes)
                if (actual != expected) {
                    _check.value = Check.Error(manager.context.getString(R.string.fw_error_integrity))
                    return
                }
            }
            if (custom && bytes.firstOrNull() != ESP_IMAGE_MAGIC) {
                _check.value = Check.Error(manager.context.getString(R.string.fw_error_not_firmware))
                return
            }
            uploadingLocal = custom
            ota.start(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation == installGeneration) {
                _check.value = Check.Error(manager.context.getString(R.string.fw_error_download, e.message))
            }
        } finally {
            if (generation == installGeneration) _downloadProgress.value = null
        }
    }

    suspend fun installRelease(release: FirmwareRelease) = install(release.manifest, custom = true)

    suspend fun installDevelopment(firmware: VerifiedPrFirmware, token: String) {
        if (busy) return
        val generation = ++installGeneration
        try {
            _downloadProgress.value = 0f
            // A PR may have advanced or closed while its confirmation was open.
            DevelopmentFirmwareRepository().ensureCurrent(firmware.build, token)
            currentCoroutineContext().ensureActive()
            if (generation != installGeneration) return
            if (firmware.bytes.firstOrNull() != ESP_IMAGE_MAGIC) {
                _check.value = Check.Error(manager.context.getString(R.string.fw_error_not_firmware))
                return
            }
            uploadingLocal = true
            ota.start(firmware.bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation == installGeneration) {
                _check.value = Check.Error(manager.context.developmentFirmwareError(e))
            }
        } finally {
            if (generation == installGeneration) _downloadProgress.value = null
        }
    }

    fun installLocal(bytes: ByteArray): Boolean {
        if (busy) return false
        if (bytes.firstOrNull() != ESP_IMAGE_MAGIC) return false
        uploadingLocal = true
        ota.start(bytes)
        return true
    }

    fun cancel() {
        installGeneration++
        ota.cancel()
        _downloadProgress.value = null
    }

    fun dispose() {
        cancel()
        versionReader.dispose()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
