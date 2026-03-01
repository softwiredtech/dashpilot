package com.softwiredtech.pilotboard.utils

import android.content.Context
import java.io.File

object FileUtils {
    fun copyAssetToFile(context: Context, assetName: String): String {
        val file = File(context.cacheDir, assetName)
        context.assets.open(assetName).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }
}