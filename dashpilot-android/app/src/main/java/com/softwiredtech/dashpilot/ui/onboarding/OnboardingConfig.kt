package com.softwiredtech.dashpilot.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

enum class MediaType { IMAGE, DEVICE_PUCK }

sealed interface MediaSpec {
    val heightDp: Int

    data class Image(
        @DrawableRes val resId: Int,
        override val heightDp: Int = 220
    ) : MediaSpec

    data class DevicePuck(
        override val heightDp: Int = 220
    ) : MediaSpec
}

data class CtaSpec(
    val label: String,
    val icon: ImageVector? = null
)

data class PairingCopy(
    val idleTitle: String,
    val idleSubtitle: String,
    val searchingTitle: String,
    val searchingSubtitle: String,
    val successTitle: String,
    val successSubtitle: String,
    val successCtaLabel: String
)

data class TipRow(
    val icon: ImageVector,
    val text: String
)

sealed class OnboardingPage {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val primaryCta: CtaSpec
    abstract val secondaryCta: CtaSpec?
    abstract val showSkip: Boolean
    abstract val showStepIndicator: Boolean
    abstract val media: MediaSpec?
    abstract val stepSlots: Int

    data class Info(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val primaryCta: CtaSpec,
        override val secondaryCta: CtaSpec? = null,
        override val showSkip: Boolean = false,
        override val showStepIndicator: Boolean = true,
        override val media: MediaSpec? = null,
        override val stepSlots: Int = 1
    ) : OnboardingPage()

    data class Pairing(
        override val id: String,
        override val primaryCta: CtaSpec,
        val pairingCopy: PairingCopy,
        override val secondaryCta: CtaSpec? = null,
        override val showSkip: Boolean = true,
        override val showStepIndicator: Boolean = true,
        override val media: MediaSpec = MediaSpec.DevicePuck(),
        override val stepSlots: Int = 2
    ) : OnboardingPage() {
        override val title: String get() = pairingCopy.idleTitle
        override val subtitle: String get() = pairingCopy.idleSubtitle
    }

    data class Tips(
        override val id: String,
        override val title: String,
        override val subtitle: String,
        override val primaryCta: CtaSpec,
        val tipRows: List<TipRow>,
        override val secondaryCta: CtaSpec? = null,
        override val showSkip: Boolean = false,
        override val showStepIndicator: Boolean = true,
        override val media: MediaSpec? = null,
        override val stepSlots: Int = 1
    ) : OnboardingPage()
}

data class OnboardingConfig(val pages: List<OnboardingPage>) {
    val totalSteps: Int get() = pages.sumOf { it.stepSlots }
}
