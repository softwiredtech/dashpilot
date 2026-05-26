package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.SystemUpdate

val DefaultOnboardingConfig = OnboardingConfig(
    pages = listOf(
        OnboardingPage.Pairing(
            id = "pair",
            primaryCta = CtaSpec("Pair device", icon = Icons.Outlined.Bluetooth),
            showSkip = true,
            pairingCopy = PairingCopy(
                idleTitle = "Pair your DashKit",
                idleSubtitle = "Plug the DashKit into your car's OBD-II port, then tap below to pair over Bluetooth.",
                searchingTitle = "Looking for DashKit…",
                searchingSubtitle = "Hold the device close to your phone. This usually takes a few seconds.",
                successTitle = "You're all set!",
                successSubtitle = "DashKit is connected and ready to stream data to your dashboard.",
                successCtaLabel = "Continue"
            )
        ),
        OnboardingPage.Tips(
            id = "tips",
            title = "One more thing",
            subtitle = "You can pair additional devices and install firmware updates anytime from Settings.",
            primaryCta = CtaSpec("Get started", icon = Icons.Outlined.CheckCircle),
            tipRows = listOf(
                TipRow(Icons.Outlined.DevicesOther, "Pair more DashKit devices"),
                TipRow(Icons.Outlined.SystemUpdate, "Check for firmware updates")
            )
        )
    )
)
