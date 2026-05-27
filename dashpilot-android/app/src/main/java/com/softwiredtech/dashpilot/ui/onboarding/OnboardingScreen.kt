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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.viewmodel.ConnectionViewModel
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors
import kotlinx.coroutines.launch

private const val PAIRING_PAGE = 0
private const val TIPS_PAGE = 1
private const val PAGE_COUNT = 2
private const val TOTAL_STEPS = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    connectionVM: ConnectionViewModel,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var pairingState by rememberSaveable { mutableStateOf(PairingState.Idle) }

    val onPairingPage = pagerState.currentPage == PAIRING_PAGE
    val canAdvance = !onPairingPage || pairingState == PairingState.Paired
    val currentStep = when {
        onPairingPage && pairingState != PairingState.Paired -> 1
        onPairingPage -> 2
        else -> 3
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingColors.BgBase)
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        TopBar(showSkip = onPairingPage, onSkip = onSkip)
        Spacer(Modifier.height(12.dp))
        StepIndicator(total = TOTAL_STEPS, current = currentStep)
        Spacer(Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = canAdvance,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val advance: () -> Unit = {
                if (pageIndex == PAGE_COUNT - 1) onFinish()
                else scope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
            }
            FadeUpOnEnter(key = pageIndex) {
                when (pageIndex) {
                    PAIRING_PAGE -> PairingPage(
                        connectionVM = connectionVM,
                        state = pairingState,
                        onStateChanged = { pairingState = it },
                        onAdvance = advance,
                    )
                    TIPS_PAGE -> TipsPage(onAdvance = advance)
                }
            }
        }
    }
}

@Composable
private fun TopBar(showSkip: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.onboarding_top_title),
            color = OnboardingColors.TextPrimary,
            fontSize = OnboardingTokens.TopTitle,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.5).sp,
        )
        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = OnboardingColors.TextMuted,
                    fontSize = OnboardingTokens.Body,
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < current) OnboardingColors.Accent else OnboardingColors.StepInactive)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.onboarding_step_progress, current, total),
            color = OnboardingColors.TextMuted,
            fontSize = OnboardingTokens.Caption,
        )
    }
}

@Composable
private fun FadeUpOnEnter(key: Any?, content: @Composable () -> Unit) {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -8 }, animationSpec = tween(350)) +
                fadeIn(animationSpec = tween(350)),
    ) { content() }
}
