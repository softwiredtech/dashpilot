package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.ui.theme.OnboardingColors

enum class PairingState { Idle, Searching, Paired }

@Composable
fun DevicePuck(
    state: PairingState,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "puck")

    val ringAnims = List(3) { i ->
        val scale = infinite.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                initialStartOffset = StartOffset(i * 600)
            ),
            label = "ringScale$i"
        )
        val alpha = infinite.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                initialStartOffset = StartOffset(i * 600)
            ),
            label = "ringAlpha$i"
        )
        scale to alpha
    }

    val ledPulse = infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "ledPulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_puck),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // Vector viewport is 220, puck radius is 68 → 68/220 ≈ 0.309
            val puckRadius = size.minDimension * 0.309f
            // LED sits in the upper-right quadrant of the puck face
            val ledCenter = Offset(
                x = center.x + 32.dp.toPx(),
                y = center.y - 22.dp.toPx()
            )

            if (state == PairingState.Searching) {
                ringAnims.forEach { (scaleAnim, alphaAnim) ->
                    drawCircle(
                        color = OnboardingColors.Accent.copy(alpha = alphaAnim.value),
                        radius = puckRadius * scaleAnim.value,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            if (state == PairingState.Paired) {
                drawCircle(
                    color = OnboardingColors.Accent.copy(alpha = 0.15f),
                    radius = 12.dp.toPx(),
                    center = ledCenter
                )
                drawCircle(
                    color = OnboardingColors.Accent.copy(alpha = 0.35f),
                    radius = 7.dp.toPx(),
                    center = ledCenter
                )
            }

            val ledColor = if (state == PairingState.Paired) OnboardingColors.Accent else OnboardingColors.LedDim
            val ledRadius = if (state == PairingState.Paired) 4.dp.toPx() * ledPulse.value else 4.dp.toPx()
            drawCircle(color = ledColor, radius = ledRadius, center = ledCenter)
        }

        AnimatedVisibility(
            visible = state == PairingState.Paired,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            modifier = Modifier.offset(x = 48.dp, y = 56.dp)
        ) {
            CheckBadge()
        }
    }
}

@Composable
private fun CheckBadge() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(width = 3.dp, color = Color.Black, shape = CircleShape)
            .background(color = OnboardingColors.Accent, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(20.dp)
        )
    }
}
