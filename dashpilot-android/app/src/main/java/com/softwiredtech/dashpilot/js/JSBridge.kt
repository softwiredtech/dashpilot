package com.softwiredtech.dashpilot.js

import android.webkit.WebView
import com.google.gson.Gson
import com.softwiredtech.dashpilot.datamodel.CarState

class JSBridge {
    private val gson = Gson()
    fun sendMessageToJs(webView: WebView, message: CarState) {
        val json = gson.toJson(message)

        val script = """
            window.onWorldMessage($json);
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}