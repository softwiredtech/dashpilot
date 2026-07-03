package com.softwiredtech.dashpilot.api

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * A single firmware release as described by the update manifest hosted on the
 * server. Only [version] and [url] are required; the rest are optional.
 */
data class FirmwareManifest(
    val version: String,
    val url: String,
    val sha256: String? = null,
    val notes: String? = null,
    val minAppVersion: String? = null
)

/**
 * Fetches the firmware update manifest and downloads firmware binaries.
 *
 * TODO(server): set [MANIFEST_URL] to the URL that serves the update manifest
 * JSON. This is the ONLY thing that needs to be filled in to enable OTA update
 * checks. The manifest must be a JSON object of the shape:
 *
 *   {
 *     "version": "1.2.3",
 *     "url": "https://.../dashkit-1.2.3.bin",
 *     "sha256": "<optional hex digest of the .bin>",
 *     "notes": "<optional human-readable release notes>",
 *     "minAppVersion": "<optional minimum DashPilot app version>"
 *   }
 */
object FirmwareUpdateRepository {
    private const val TAG = "FirmwareUpdateRepo"

    const val MANIFEST_URL =
        "https://firebasestorage.googleapis.com/v0/b/xreport-f792c.appspot.com/o/dashkit%2Fmanifest.json?alt=media&token=68bcc03a-3951-454d-8f35-f5ab06c0ed0a"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    /** Download and parse the update manifest. Returns null on any failure. */
    suspend fun fetchManifest(): FirmwareManifest? = withContext(Dispatchers.IO) {
        if (MANIFEST_URL.contains("EXAMPLE.com")) {
            Log.w(TAG, "MANIFEST_URL not configured; skipping update check")
            return@withContext null
        }
        try {
            val request = Request.Builder().url(MANIFEST_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Manifest fetch failed: HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                gson.fromJson(body, FirmwareManifest::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manifest fetch error: ${e.message}")
            null
        }
    }

    /**
     * Download the firmware binary at [url], reporting fractional progress
     * (0f..1f) via [onProgress] when the content length is known. Throws on
     * failure so the caller can surface an error.
     */
    suspend fun downloadBinary(
        url: String,
        onProgress: (Float) -> Unit = {}
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            val total = body.contentLength()
            val source = body.byteStream()
            val out = ByteArrayOutputStream(if (total > 0) total.toInt() else 64 * 1024)
            val buffer = ByteArray(16 * 1024)
            var read: Int
            var downloaded = 0L
            while (source.read(buffer).also { read = it } != -1) {
                out.write(buffer, 0, read)
                downloaded += read
                if (total > 0) onProgress(downloaded.toFloat() / total.toFloat())
            }
            out.toByteArray()
        }
    }
}
