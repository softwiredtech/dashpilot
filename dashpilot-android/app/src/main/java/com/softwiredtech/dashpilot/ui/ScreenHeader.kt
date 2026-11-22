package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.softwiredtech.dashpilot.R

/** Back navigation for detail screens; page titles are provided by the app navigation. */
@Composable
fun ScreenHeader(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        // Pull the button left by its built-in content inset so the arrow
        // glyph — not the 48dp touch target — lines up with the content edge.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-12).dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

    }
}
