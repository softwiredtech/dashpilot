package com.softwiredtech.dashpilot.datamodel.dash

import android.content.Context
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException

object ManifestLoader {
    fun loadFromAssets(
        context: Context,
        dashboardIds: List<String>,
        folderPrefix: String,
        optional: Boolean = false,
    ): Map<String, DashboardManifest> {
        val result = mutableMapOf<String, DashboardManifest>()
        for (id in dashboardIds) {
            val path = "$folderPrefix-$id/manifest.json"
            try {
                val json = try {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                } catch (e: FileNotFoundException) {
                    if (optional) continue else throw e
                }
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
