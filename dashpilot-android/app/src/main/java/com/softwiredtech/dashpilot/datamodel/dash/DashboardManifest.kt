package com.softwiredtech.dashpilot.datamodel.dash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
enum class ManifestDisplaySetting {
    @SerialName("showPhoneBattery") SHOW_PHONE_BATTERY,
    @SerialName("showCarBattery") SHOW_CAR_BATTERY,
    @SerialName("showOdometer") SHOW_ODOMETER,
    @SerialName("alwaysOnBlindSpotMonitor") ALWAYS_ON_BLIND_SPOT_MONITOR,
    @SerialName("useImperial") USE_IMPERIAL,
    @SerialName("darkMode") DARK_MODE,
    @SerialName("renderQuality") RENDER_QUALITY,
    @SerialName("darkModeBackgroundGray") DARK_MODE_BACKGROUND_GRAY,
}

class ManifestParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Serializable
data class DashboardManifest(
    val manifestVersion: Int,
    val id: String,
    val settings: Set<ManifestDisplaySetting>,
) {
    companion object {
        private val decoder = Json { ignoreUnknownKeys = true }

        fun parse(json: String): DashboardManifest {
            try {
                val manifest = decoder.decodeFromString<DashboardManifest>(json)
                if (manifest.manifestVersion != 1) {
                    throw ManifestParseException(
                        "Unsupported manifestVersion ${manifest.manifestVersion}; expected 1"
                    )
                }
                return manifest
            } catch (e: ManifestParseException) {
                throw e
            } catch (e: SerializationException) {
                throw ManifestParseException("Failed to parse dashboard manifest: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw ManifestParseException("Invalid dashboard manifest: ${e.message}", e)
            }
        }
    }
}
