package com.softwiredtech.dashpilot.datamodel.dash

import android.content.Context
import android.util.Log
import java.io.IOException

object ManifestLoader {

    fun loadFromMap(jsonById: Map<String, String>): Map<String, DashboardManifest> =
        jsonById.mapValues { (_, json) -> DashboardManifest.parse(json) }

    fun loadFromAssets(context: Context, dashboardIds: List<String>): Map<String, DashboardManifest> {
        val result = mutableMapOf<String, DashboardManifest>()
        for (id in dashboardIds) {
            val path = "web-$id/manifest.json"
            try {
                val json = context.assets.open(path).bufferedReader().use { it.readText() }
                result[id] = DashboardManifest.parse(json)
            } catch (e: IOException) {
                Log.w("DashPilot", "Missing or unreadable manifest at assets/$path for dashboard '$id'", e)
            } catch (e: ManifestParseException) {
                Log.w("DashPilot", "Failed to parse manifest for dashboard '$id': ${e.message}", e)
            }
        }
        return result
    }
}
