package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors

@Composable
fun TipsPage(onAdvance: () -> Unit) {
    OnboardingPageScaffold(
        title = stringResource(R.string.onboarding_tips_title),
        subtitle = stringResource(R.string.onboarding_tips_subtitle),
        extra = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TipRow(Icons.Outlined.DevicesOther, stringResource(R.string.onboarding_tips_row_pair))
                TipRow(Icons.Outlined.SystemUpdate, stringResource(R.string.onboarding_tips_row_firmware))
            }
        },
        cta = {
            PrimaryCta(
                label = stringResource(R.string.onboarding_tips_cta),
                icon = Icons.Outlined.CheckCircle,
                onClick = onAdvance,
            )
        },
    )
}

@Composable
private fun TipRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OnboardingColors.Surface, RoundedCornerShape(OnboardingTokens.RadiusSmall))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    OnboardingColors.Accent.copy(alpha = 0.15f),
                    RoundedCornerShape(OnboardingTokens.RadiusSmall),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = OnboardingColors.Accent, modifier = Modifier.size(14.dp))
        }
        Text(text, color = OnboardingColors.TextPrimary, fontSize = OnboardingTokens.Body)
    }
}
