package com.softwiredtech.pilotboard.js

import android.webkit.WebView
import com.google.gson.Gson
import com.softwiredtech.pilotboard.datamodel.WorldMessage

class JSBridge {
    private val gson = Gson()
    fun sendMessageToJs(webView: WebView, message: WorldMessage) {
        val json = gson.toJson(message)

        val script = """
            window.onWorldMessage($json);
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}