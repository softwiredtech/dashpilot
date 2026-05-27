package com.softwiredtech.dashpilot.datamodel.dash

import android.content.Context
import android.util.Log
import java.io.IOException

object ManifestLoader {
    fun loadFromAssets(context: Context, dashboardIds: List<String>): Map<String, DashboardManifest> {
        val result = mutableMapOf<String, DashboardManifest>()
        for (id in dashboardIds) {
            val path = "web-$id/manifest.json"
            try {
                val json = context.assets.open(path).bufferedReader().use { it.readText() }
                val manifest = DashboardManifest.parse(json)
                if (manifest.id != id) {
                    Log.w("DashPilot", "Manifest at assets/$path declares id '${manifest.id}' but expected '$id'")
                    continue
                }
                result[id] = manifest
            } catch (e: IOException) {
                Log.w("DashPilot", "Missing or unreadable manifest at assets/$path for dashboard '$id'", e)
            } catch (e: ManifestParseException) {
                Log.w("DashPilot", "Failed to parse manifest for dashboard '$id': ${e.message}", e)
            }
        }
        return result
    }
}
