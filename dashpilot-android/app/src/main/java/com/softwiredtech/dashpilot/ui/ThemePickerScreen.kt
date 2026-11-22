package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datamodel.dash.*
import com.softwiredtech.dashpilot.ui.theme.DarkColors

@Composable
fun ThemePickerScreen(onBack: () -> Unit, onPreview: (DashboardConfig) -> Unit) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(getSelectedDashboard(context).id) }
    Column(Modifier.fillMaxSize().background(DarkColors.Background).systemBarsPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenHeader(onBack = onBack)
        Spacer(Modifier.height(24.dp))
        DashboardGallery(selectedId, onSelect = { dashboard ->
            saveSelectedDashboard(context, dashboard)
            selectedId = dashboard.id
        }, onPreview = onPreview)
    }
}
