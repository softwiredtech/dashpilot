package com.softwiredtech.dashpilot.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.webkit.WebViewAssetLoader
import com.softwiredtech.dashpilot.datamodel.dash.DASH_PREFS_NAME
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datamodel.dash.PREF_DASH_APP_DATA_PREFIX
import com.softwiredtech.dashpilot.js.CarStateBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

private const val ASSET_LOADER_DOMAIN = "appassets.androidplatform.net"
const val LOCAL_ASSET_BASE_URL = "https://$ASSET_LOADER_DOMAIN/assets/"

private fun localDashAppId(url: String): String? =
    url.removePrefix(LOCAL_ASSET_BASE_URL).takeIf { it != url }?.substringBefore('/')?.takeIf { it.isNotEmpty() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebDashView(
    modifier: Modifier = Modifier,
    url: String,
    scope: CoroutineScope,
    dashStateFlow: Flow<DashState>,
    onEditingChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val currentOnEditingChange by rememberUpdatedState(onEditingChange)
    // Bridge calls arrive on the WebView's JavaBridge thread; state goes to the main one.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val carStateBridge = remember {
        val prefs = context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        val dataKey = localDashAppId(url)?.let { PREF_DASH_APP_DATA_PREFIX + it }
        CarStateBridge(
            loadAppData = { dataKey?.let { prefs.getString(it, "") } ?: "" },
            storeAppData = { json -> dataKey?.let { prefs.edit { putString(it, json) } } },
            onEditingChange = { editing -> mainHandler.post { currentOnEditingChange(editing) } },
        )
    }
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
                        currentOnEditingChange(false)
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
            // Drop a pending "editing" post so it can't land after the reset.
            mainHandler.removeCallbacksAndMessages(null)
            currentOnEditingChange(false)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeJavascriptInterface("NativeCarState")
            webView.destroy()
        }
    }

    LaunchedEffect(pageLoaded) {
        if (pageLoaded) {
            dashStateFlow.collect { dashState ->
                carStateBridge.update(dashState.carState)
                carStateBridge.updatePhoneBattery(dashState.phoneBattery)
                carStateBridge.updateCurrentTime(dashState.currentTime)
                carStateBridge.updateDisplaySettings(dashState.displaySettings)
                carStateBridge.updateSpeedCameraDistance(dashState.speedCameraDistance)
                carStateBridge.updateDataSourceType(dashState.dataSourceType)
                webView.post {
                    webView.evaluateJavascript("window.onCarStateUpdate && window.onCarStateUpdate()", null)
                }
            }
        }
    }
}
