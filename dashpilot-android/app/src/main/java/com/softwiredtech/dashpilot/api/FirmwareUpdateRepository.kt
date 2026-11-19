package com.softwiredtech.dashpilot.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.softwiredtech.dashpilot.datamodel.dash.CarModel
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

data class FirmwareRelease(
    val tag: String,
    val name: String,
    val prerelease: Boolean,
    val manifest: FirmwareManifest,
) {
    val label: String get() = if (name == tag) tag else "$tag — $name"
}

/**
 * Fetches the firmware update manifest and downloads firmware binaries.
 *
 * Each car has its own manifest ([manifestUrl]). The manifest must be a JSON
 * object of the shape:
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
    private const val RELEASES_URL = "https://api.github.com/repos/softwiredtech/dashkit-firmware/releases"
    private const val DOWNLOAD_PREFIX = "https://github.com/softwiredtech/dashkit-firmware/releases/download/"
    private const val OTA_ASSET = "dashkit-firmware.bin"

    // One release track (storage folder) per car; must match tools/publish_ota.sh
    // in the firmware repo. Legacy keeps the original folder older apps read.
    fun manifestUrl(model: CarModel): String {
        val folder = when (model) {
            CarModel.TESLA_LEGACY -> "dashkit"
            CarModel.TESLA_JUNIPER -> "dashkit-juniper"
        }
        return "https://firebasestorage.googleapis.com/v0/b/dashkit-connect.firebasestorage.app/o/" +
            "$folder%2Fmanifest.json?alt=media&token=68bcc03a-3951-454d-8f35-f5ab06c0ed0a"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    suspend fun fetchReleases(): List<FirmwareRelease> = withContext(Dispatchers.IO) {
        val releases = mutableListOf<FirmwareRelease>()
        var page = 1
        do {
            val request = Request.Builder().url("$RELEASES_URL?per_page=100&page=$page")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DashPilot")
                .build()
            val more = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub releases: HTTP ${response.code}" }
                releases += parseReleases(response.body?.string() ?: error("Empty GitHub response"))
                response.header("Link").orEmpty().contains("rel=\"next\"")
            }
            page++
        } while (more)
        releases
    }

    internal fun parseReleases(json: String): List<FirmwareRelease> =
        JsonParser.parseString(json).asJsonArray.mapNotNull { entry ->
            val release = entry.asJsonObject
            if (release["draft"]?.asBoolean == true) return@mapNotNull null
            val tag = release["tag_name"]?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val asset = release.getAsJsonArray("assets")?.firstOrNull {
                it.asJsonObject["name"]?.asString == OTA_ASSET
            }?.asJsonObject ?: return@mapNotNull null
            val url = asset["browser_download_url"]?.asString ?: return@mapNotNull null
            if (!url.startsWith(DOWNLOAD_PREFIX) || (asset["size"]?.asLong ?: 0) <= 0) return@mapNotNull null
            val digest = asset["digest"]?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
                ?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            val name = release["name"]?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: tag
            FirmwareRelease(tag, name, release["prerelease"]?.asBoolean == true,
                FirmwareManifest(tag, url, sha256 = digest))
        }

    /** Download and parse [model]'s update manifest. Returns null on any failure. */
    suspend fun fetchManifest(model: CarModel): FirmwareManifest? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(manifestUrl(model)).get().build()
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
