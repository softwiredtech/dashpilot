package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.datamodel.DashboardConfig
import com.softwiredtech.dashpilot.datamodel.DashboardType
import com.softwiredtech.dashpilot.datamodel.availableDashboards

@Composable
fun DashboardSelectionScreen(
    onSelect: (DashboardConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select Dashboard",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose a dashboard to display",
            color = Color(0xFF888888),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(availableDashboards) { dashboard ->
                DashboardCard(dashboard = dashboard, onClick = { onSelect(dashboard) })
            }
        }
    }
}

@Composable
private fun DashboardCard(
    dashboard: DashboardConfig,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = dashboard.screenshotRes),
            contentDescription = dashboard.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = dashboard.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(4.dp))

        val badgeColor = when (dashboard.type) {
            DashboardType.WEB -> Color(0xFF1A237E)
            DashboardType.RIVE -> Color(0xFFB71C1C)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeColor)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = dashboard.type.name,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
