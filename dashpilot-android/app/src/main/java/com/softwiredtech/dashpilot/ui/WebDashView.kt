package com.softwiredtech.dashpilot.ui

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.js.CarStateBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

private const val ASSET_LOADER_DOMAIN = "appassets.androidplatform.net"
const val LOCAL_ASSET_BASE_URL = "https://$ASSET_LOADER_DOMAIN/assets/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebDashView(
    modifier: Modifier = Modifier,
    url: String,
    scope: CoroutineScope,
    dashStateFlow: Flow<DashState>
) {
    val carStateBridge = remember { CarStateBridge() }
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val webView = remember { WebView(context) }
    var pageLoaded by remember { mutableStateOf(false) }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_LOADER_DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    AndroidView(
        factory = { _ ->
            webView.apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val response = request?.let { assetLoader.shouldInterceptRequest(it.url) }
                            ?: return super.shouldInterceptRequest(view, request)
                        // Fix MIME type for .wasm files (AssetsPathHandler doesn't know application/wasm)
                        val path = request.url.path ?: ""
                        if (path.endsWith(".wasm")) {
                            response.mimeType = "application/wasm"
                        } else if (path.endsWith(".glb")) {
                            response.mimeType = "model/gltf-binary"
                        } else if (path.endsWith(".wgsl")) {
                            response.mimeType = "text/plain"
                        }
                        return response
                    }

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
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                addJavascriptInterface(carStateBridge, "NativeCarState")
                loadUrl(url)
            }
        },
        modifier = modifier
    )

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface("NativeCarState")
            webView.destroy()
        }
    }

    LaunchedEffect(pageLoaded, isSystemDark) {
        if (pageLoaded) {
            dashStateFlow.collect { dashState ->
                carStateBridge.update(dashState.carState)
                carStateBridge.updatePhoneBattery(dashState.phoneBattery)
                carStateBridge.updateCurrentTime(dashState.currentTime)
                carStateBridge.updateDisplaySettings(dashState.displaySettings, isSystemDark)
                carStateBridge.updateSpeedCameraDistance(dashState.speedCameraDistance)
                webView.post {
                    webView.evaluateJavascript("window.onCarStateUpdate && window.onCarStateUpdate()", null)
                }
            }
        }
    }
}
