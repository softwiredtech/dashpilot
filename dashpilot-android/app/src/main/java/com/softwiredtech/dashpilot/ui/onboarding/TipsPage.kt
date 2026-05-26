package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TipsPage(
    page: OnboardingPage.Tips,
    onAdvance: () -> Unit
) {
    OnboardingPageScaffold(
        title = page.title,
        subtitle = page.subtitle,
        hero = null,
        extra = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                page.tipRows.forEach { TipRowItem(it) }
            }
        },
        cta = {
            Button(
                onClick = onAdvance,
                shape = RoundedCornerShape(OnboardingTokens.RadiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnboardingTokens.Accent,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (page.primaryCta.icon != null) {
                    Icon(
                        imageVector = page.primaryCta.icon,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                }
                Text(
                    text = page.primaryCta.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
private fun TipRowItem(row: TipRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = OnboardingTokens.Surface,
                shape = RoundedCornerShape(OnboardingTokens.RadiusSmall)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = OnboardingTokens.Accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(OnboardingTokens.RadiusSmall)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = OnboardingTokens.Accent,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = row.text,
            color = OnboardingTokens.TextPrimary,
            fontSize = OnboardingTokens.Body
        )
    }
}
