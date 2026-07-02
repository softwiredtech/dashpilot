package com.softwiredtech.dashpilot.ble

import android.util.Log
import com.softwiredtech.dashpilot.api.FirmwareManifest
import com.softwiredtech.dashpilot.api.FirmwareUpdateRepository
import com.softwiredtech.dashpilot.datasource.DashKitBleManager
import com.softwiredtech.dashpilot.util.SemVer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

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

    /** Begin reading the installed firmware version over BLE. */
    fun start() {
        versionReader.read()
    }

    /**
     * Fetch the manifest and compare with the installed version. Reads the
     * installed version first (waiting briefly if it hasn't arrived yet).
     */
    suspend fun checkForUpdate() {
        _check.value = Check.Checking
        val manifest = FirmwareUpdateRepository.fetchManifest()
        if (manifest == null) {
            _check.value = Check.Error("Could not reach update server")
            return
        }
        val current = installedVersion.value ?: versionReader.await()
        if (current == null) {
            Log.w(TAG, "Installed version unknown; offering update anyway")
            _check.value = Check.Available(manifest)
            return
        }
        _check.value = if (SemVer.isNewer(manifest.version, current)) {
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
    suspend fun install(manifest: FirmwareManifest) {
        try {
            _downloadProgress.value = 0f
            val bytes = FirmwareUpdateRepository.downloadBinary(manifest.url) { p ->
                _downloadProgress.value = p
            }
            _downloadProgress.value = null

            val expected = manifest.sha256?.trim()?.lowercase()
            if (!expected.isNullOrEmpty()) {
                val actual = sha256Hex(bytes)
                if (actual != expected) {
                    _check.value = Check.Error("Downloaded firmware failed integrity check")
                    return
                }
            }
            ota.start(bytes)
        } catch (e: Exception) {
            _downloadProgress.value = null
            _check.value = Check.Error("Download failed: ${e.message}")
        }
    }

    fun cancel() {
        ota.cancel()
        _downloadProgress.value = null
    }

    fun dispose() {
        ota.cancel()
        versionReader.dispose()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
