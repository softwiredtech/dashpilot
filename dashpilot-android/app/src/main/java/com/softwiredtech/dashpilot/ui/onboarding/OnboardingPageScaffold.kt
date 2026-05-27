package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors

/**
 * Shared vertical layout: hero takes the upper flex region, title/subtitle anchor
 * mid-screen, optional extra content goes between subtitle and CTA, primary CTA pins bottom.
 */
@Composable
fun OnboardingPageScaffold(
    title: String,
    subtitle: String,
    cta: @Composable () -> Unit,
    hero: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            hero?.invoke()
        }
        Text(
            text = title,
            color = OnboardingColors.TextPrimary,
            fontSize = OnboardingTokens.PageTitle,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = OnboardingColors.TextSecondary,
            fontSize = OnboardingTokens.Body,
            textAlign = TextAlign.Center,
            lineHeight = (OnboardingTokens.Body.value * 1.5f).sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (extra != null) {
            Spacer(Modifier.height(20.dp))
            extra()
        }
        Spacer(Modifier.height(24.dp))
        cta()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PrimaryCta(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    showSpinner: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(OnboardingTokens.RadiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = OnboardingColors.Accent,
            contentColor = Color.Black,
            disabledContainerColor = OnboardingColors.Accent,
            disabledContentColor = Color.Black,
        ),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        when {
            showSpinner -> {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(10.dp))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
            }
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
