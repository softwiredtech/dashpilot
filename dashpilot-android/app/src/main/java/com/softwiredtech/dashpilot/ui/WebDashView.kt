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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val webView = remember { WebView(context) }
    var pageLoaded by remember { mutableStateOf(false) }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .setDomain(ASSET_LOADER_DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    var changingLane by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
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
                            val response =
                                request?.let { assetLoader.shouldInterceptRequest(it.url) }
                                    ?: return super.shouldInterceptRequest(view, request)
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
            modifier = Modifier.matchParentSize()
        )

        AnimatedVisibility(
            visible = changingLane,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Surface(
                color = Color(0xE6FF8C00.toInt()),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Changing lane",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    LaunchedEffect(pageLoaded) {
        if (pageLoaded) {
            dashStateFlow.collect { dashState ->
                carStateBridge.update(dashState.carState)
                carStateBridge.updatePhoneBattery(dashState.phoneBattery)
                carStateBridge.updateCurrentTime(dashState.currentTime)
                carStateBridge.updateDisplaySettings(dashState.displaySettings)
                changingLane = dashState.carState.changingLane
                carStateBridge.updateSpeedCameraDistance(dashState.speedCameraDistance)
                webView.post {
                    webView.evaluateJavascript("window.onCarStateUpdate && window.onCarStateUpdate()", null)
                }
            }
        }
    }
}
