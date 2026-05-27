package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.softwiredtech.dashpilot.R

@Composable
fun defaultOnboardingConfig(): OnboardingConfig = OnboardingConfig(
    pages = listOf(
        OnboardingPage.Pairing(
            id = "pair",
            primaryCta = CtaSpec(
                label = stringResource(R.string.onboarding_pair_cta),
                icon = Icons.Outlined.Bluetooth
            ),
            showSkip = true,
            pairingCopy = PairingCopy(
                idleTitle = stringResource(R.string.onboarding_pair_idle_title),
                idleSubtitle = stringResource(R.string.onboarding_pair_idle_subtitle),
                searchingTitle = stringResource(R.string.onboarding_pair_searching_title),
                searchingSubtitle = stringResource(R.string.onboarding_pair_searching_subtitle),
                successTitle = stringResource(R.string.onboarding_pair_success_title),
                successSubtitle = stringResource(R.string.onboarding_pair_success_subtitle),
                successCtaLabel = stringResource(R.string.onboarding_pair_success_cta)
            )
        ),
        OnboardingPage.Tips(
            id = "tips",
            title = stringResource(R.string.onboarding_tips_title),
            subtitle = stringResource(R.string.onboarding_tips_subtitle),
            primaryCta = CtaSpec(
                label = stringResource(R.string.onboarding_tips_cta),
                icon = Icons.Outlined.CheckCircle
            ),
            tipRows = listOf(
                TipRow(Icons.Outlined.DevicesOther, stringResource(R.string.onboarding_tips_row_pair)),
                TipRow(Icons.Outlined.SystemUpdate, stringResource(R.string.onboarding_tips_row_firmware))
            )
        )
    )
)
