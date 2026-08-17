package com.softwiredtech.dashpilot.ui

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datamodel.dash.DashboardType
import com.softwiredtech.dashpilot.datamodel.dash.availableDashboards
import com.softwiredtech.dashpilot.datamodel.dash.dashboardById
import com.softwiredtech.dashpilot.datamodel.dash.saveSelectedDashboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

// Only web dashboards participate in the swipe carousel.
private val swipeableDashboards = availableDashboards.filter { it.type == DashboardType.WEB }

private const val WEB_TYPE = "web"

@Composable
fun DashboardScreen(
    dashboardType: String,
    dashboardUrl: String,
    dashStateFlow: Flow<DashState>
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (!isLandscape) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "↺",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_rotate_prompt),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Set after the first swipe; null means "show what the route passed in".
    var swipedDashboardId by rememberSaveable { mutableStateOf<String?>(null) }
    var swipeCount by remember { mutableIntStateOf(0) }
    var switchedToName by remember { mutableStateOf("") }
    var nameOverlayVisible by remember { mutableStateOf(false) }

    val swipedDashboard = remember(swipedDashboardId) { dashboardById(swipedDashboardId) }
    val currentType = swipedDashboard?.type?.name?.lowercase() ?: dashboardType
    val currentUrl = swipedDashboard?.url ?: dashboardUrl

    // Moves step (+1 next, -1 previous) through the web dashboards, wrapping at
    // both ends. Non-web screens (e.g. the Rive dev view) are not part of the
    // cycle and cannot be swiped away from.
    val switchDashboard by rememberUpdatedState(fun(step: Int) {
        val dashboards = swipeableDashboards
        if (dashboards.size < 2 || currentType != WEB_TYPE) return
        val currentIndex = dashboards.indexOfFirst { it.url == currentUrl }
        if (currentIndex < 0) return
        val next = dashboards[(currentIndex + step + dashboards.size) % dashboards.size]
        swipedDashboardId = next.id
        swipeCount++
        saveSelectedDashboard(context, next)
    })

    LaunchedEffect(swipeCount) {
        if (swipeCount == 0) return@LaunchedEffect
        val nameRes = dashboardById(swipedDashboardId)?.nameRes ?: return@LaunchedEffect
        switchedToName = context.getString(nameRes)
        nameOverlayVisible = true
        delay(1500)
        nameOverlayVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Observe in the Initial pass so the WebView underneath keeps
            // receiving every touch; the swipe is recognized purely by
            // watching the first pointer travel between down and up.
            .pointerInput(Unit) {
                val threshold = 60.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        last = change.position
                        if (!change.pressed) break
                    }
                    val dx = last.x - down.position.x
                    val dy = last.y - down.position.y
                    if (abs(dx) > threshold && abs(dx) > abs(dy)) {
                        switchDashboard(if (dx < 0) 1 else -1)
                    }
                }
            }
    ) {
        // Keyed on the URL so a swipe tears down the old WebView and builds a
        // fresh one for the next dashboard.
        key(currentUrl) {
            when (currentType) {
                WEB_TYPE -> {
                    WebDashView(
                        modifier = Modifier.fillMaxSize(),
                        url = currentUrl,
                        scope = scope,
                        dashStateFlow = dashStateFlow
                    )
                }
                "rive", "dev_rive" -> {
                    RiveDashView(
                        assetName = if (currentType == "dev_rive") "" else currentUrl,
                        dashStateFlow = dashStateFlow,
                        scope = scope,
                        onError = { message -> Log.d("Rive", message) },
                        fileUri = if (currentType == "dev_rive") currentUrl else null
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = nameOverlayVisible,
            enter = fadeIn(tween(durationMillis = 150)),
            exit = fadeOut(tween(durationMillis = 400)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Text(
                text = switchedToName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
