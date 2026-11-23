package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.DashboardConfig
import com.softwiredtech.dashpilot.datamodel.dash.availableDashboards
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

@Composable
fun DashboardGallery(
    selectedDashboardId: String,
    onSelect: (DashboardConfig) -> Unit,
    onPreview: (DashboardConfig) -> Unit
) {
    val count = availableDashboards.size
    val middle = Int.MAX_VALUE / 2
    val pager = rememberPagerState(
        initialPage = middle - middle % count + availableDashboards.indexOfFirst { it.id == selectedDashboardId }.coerceAtLeast(0),
        pageCount = { Int.MAX_VALUE }
    )
    val scope = rememberCoroutineScope()
    val focused = availableDashboards[pager.settledPage % count]
    val selected = focused.id == selectedDashboardId

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth * 0.72f).coerceAtMost(480.dp)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(cardWidth),
                beyondViewportPageCount = 1,
                contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                modifier = Modifier.fillMaxWidth().height(cardWidth * 9f / 16f + 32.dp)
            ) { page ->
                val dashboard = availableDashboards[page % count]
                val offset = (page - pager.currentPage) - pager.currentPageOffsetFraction
                val distance = abs(offset).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .zIndex(1f - abs(offset))
                        .graphicsLayer {
                            translationX = -offset * size.width * 0.57f
                            translationY = distance * 12.dp.toPx()
                            scaleX = 1f - distance * 0.18f
                            scaleY = 1f - distance * 0.18f
                            alpha = if (abs(offset) > 1.01f) 0f else 1f - distance * 0.35f
                        }
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)).background(DarkColors.Surface)
                        .border(2.dp, Color.White.copy(alpha = 1f - distance * 0.65f), RoundedCornerShape(12.dp))
                        // Tapping a card only browses; the preview opens solely via the explicit
                        // Preview button so swiping through the gallery never launches it by accident.
                        .clickable(enabled = page != pager.settledPage && abs(offset) <= 1.01f) {
                            scope.launch { pager.animateScrollToPage(page) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (dashboard.screenshotRes != 0) {
                        Image(
                            painterResource(dashboard.screenshotRes),
                            contentDescription = stringResource(dashboard.nameRes),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(stringResource(dashboard.nameRes), color = Color.White)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(focused.nameRes), color = Color.White, fontSize = 16.sp,
                modifier = Modifier.weight(1f))
            Text(stringResource(R.string.dashboard_gallery_position, pager.settledPage % count + 1, count),
                color = DarkColors.TextMuted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onPreview(focused) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_gallery_preview), color = Color.White)
            }
            Button(
                onClick = { onSelect(focused) },
                enabled = !selected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Text(stringResource(if (selected) R.string.dashboard_gallery_selected else R.string.dashboard_gallery_use))
            }
        }
        Text(stringResource(R.string.dashboard_gallery_hint), color = DarkColors.TextMuted, fontSize = 13.sp)
    }
}
