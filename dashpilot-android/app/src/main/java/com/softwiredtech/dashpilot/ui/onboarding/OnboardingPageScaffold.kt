package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared vertical layout for every onboarding page.
 * - Hero (illustration) sits in the upper flex region, vertically centered.
 * - Title + subtitle anchor at the same vertical position across pages.
 * - Optional extra content (e.g. tip rows) inserts between subtitle and CTA.
 * - Primary CTA pins to the bottom; optional secondary CTA sits just below it.
 */
@Composable
fun OnboardingPageScaffold(
    title: String,
    subtitle: String,
    cta: @Composable () -> Unit,
    hero: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    secondaryCta: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            hero?.invoke()
        }

        Text(
            text = title,
            color = OnboardingTokens.TextPrimary,
            fontSize = OnboardingTokens.PageTitle,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            color = OnboardingTokens.TextSecondary,
            fontSize = OnboardingTokens.Body,
            textAlign = TextAlign.Center,
            lineHeight = (OnboardingTokens.Body.value * 1.5f).sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (extra != null) {
            Spacer(modifier = Modifier.height(20.dp))
            extra()
        }

        Spacer(modifier = Modifier.height(24.dp))

        cta()

        if (secondaryCta != null) {
            Spacer(modifier = Modifier.height(8.dp))
            secondaryCta()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
