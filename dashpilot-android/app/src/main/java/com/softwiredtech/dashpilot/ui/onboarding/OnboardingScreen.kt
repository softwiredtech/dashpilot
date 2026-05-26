package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.viewmodel.ConnectionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    config: OnboardingConfig,
    connectionVM: ConnectionViewModel,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { config.pages.size })
    val scope = rememberCoroutineScope()

    val canAdvance = remember { mutableStateMapOf<Int, Boolean>() }
    var pairingSubStep by rememberSaveable { mutableIntStateOf(0) }

    val currentCanAdvance = canAdvance[pagerState.currentPage] ?: true

    val currentPage = config.pages[pagerState.currentPage]

    val currentStepSlot = run {
        var slot = 0
        config.pages.take(pagerState.currentPage).forEach { slot += it.stepSlots }
        slot += 1
        if (currentPage is OnboardingPage.Pairing && pairingSubStep == 1) {
            slot += 1
        }
        slot
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingTokens.BgBase)
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        OnboardingTopBar(
            showSkip = currentPage.showSkip,
            onSkip = onSkip
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (currentPage.showStepIndicator) {
            StepIndicator(
                total = config.totalSteps,
                current = currentStepSlot
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = currentCanAdvance,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val page = config.pages[pageIndex]
            FadeUpOnEnter(key = pageIndex) {
                when (page) {
                    is OnboardingPage.Pairing -> PairingPage(
                        page = page,
                        connectionVM = connectionVM,
                        onCanAdvanceChanged = { ok -> canAdvance[pageIndex] = ok },
                        onPairingStateChanged = { st ->
                            if (pageIndex == pagerState.currentPage) {
                                pairingSubStep = if (st == PairingState.Paired) 1 else 0
                            }
                        },
                        onAdvance = {
                            if (pageIndex == config.pages.lastIndex) {
                                onFinish()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                            }
                        }
                    )
                    is OnboardingPage.Tips -> TipsPage(
                        page = page,
                        onAdvance = {
                            if (pageIndex == config.pages.lastIndex) {
                                onFinish()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                            }
                        }
                    )
                    is OnboardingPage.Info -> InfoPage(
                        page = page,
                        onAdvance = {
                            if (pageIndex == config.pages.lastIndex) {
                                onFinish()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(
    showSkip: Boolean,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Setup",
            color = OnboardingTokens.TextPrimary,
            fontSize = OnboardingTokens.TopTitle,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.5).sp
        )
        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip",
                    color = OnboardingTokens.TextMuted,
                    fontSize = OnboardingTokens.Body
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(total: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            val active = i < current
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (active) OnboardingTokens.Accent
                        else OnboardingTokens.StepInactive
                    )
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$current of $total",
            color = OnboardingTokens.TextMuted,
            fontSize = OnboardingTokens.Caption
        )
    }
}

@Composable
private fun FadeUpOnEnter(key: Any?, content: @Composable () -> Unit) {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -8 },
            animationSpec = tween(durationMillis = 350)
        ) + fadeIn(animationSpec = tween(durationMillis = 350))
    ) {
        content()
    }
}

