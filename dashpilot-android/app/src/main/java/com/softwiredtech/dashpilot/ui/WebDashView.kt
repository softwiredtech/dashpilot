package com.softwiredtech.dashpilot.ui

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.softwiredtech.dashpilot.datamodel.CarState
import com.softwiredtech.dashpilot.js.CarStateBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebDashView(modifier: Modifier = Modifier, url: String, scope: CoroutineScope, carStateFlow: Flow<CarState>) {
    val carStateBridge = remember { CarStateBridge() }
    val context = LocalContext.current
    val webView = remember { WebView(context) }
    var pageLoaded by remember { mutableStateOf(false) }

    AndroidView(
        factory = { _ ->
            webView.apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded = true
                    }

                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(
                            "WebViewConsole",
                            "${consoleMessage.message()} -- " +
                                    "From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                        )
                        return true
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                addJavascriptInterface(carStateBridge, "NativeCarState")
                loadUrl(url)
            }
        },
        modifier = modifier
    )

    LaunchedEffect(pageLoaded) {
        if (pageLoaded) {
            carStateFlow.collect { message ->
                carStateBridge.update(message)
                webView.post {
                    webView.evaluateJavascript("window.onCarStateUpdate && window.onCarStateUpdate()", null)
                }
            }
        }
    }
}
