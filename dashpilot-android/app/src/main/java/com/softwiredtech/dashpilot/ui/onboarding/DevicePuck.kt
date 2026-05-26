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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val side = minOf(w, h)
            val puckRadius = side * 0.32f
            val center = Offset(w / 2f, h / 2f)

            val ledAngleDeg = -35.0
            val ledDistance = puckRadius * 0.55f
            val ledCenter = Offset(
                x = center.x + (ledDistance * kotlin.math.cos(Math.toRadians(ledAngleDeg))).toFloat(),
                y = center.y + (ledDistance * kotlin.math.sin(Math.toRadians(ledAngleDeg))).toFloat()
            )

            if (state == PairingState.Searching) {
                ringAnims.forEach { (scaleAnim, alphaAnim) ->
                    drawCircle(
                        color = OnboardingTokens.Accent.copy(alpha = alphaAnim.value),
                        radius = puckRadius * scaleAnim.value,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Soft shadow under puck
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = puckRadius,
                center = center.copy(y = center.y + 6f)
            )

            // Puck body
            drawCircle(
                color = OnboardingTokens.PuckBody,
                radius = puckRadius,
                center = center
            )

            // Top-left highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - puckRadius * 0.3f, center.y - puckRadius * 0.4f),
                    radius = puckRadius * 0.8f
                ),
                radius = puckRadius,
                center = center
            )

            // Subtle dot texture
            val dotCount = 18
            for (i in 0 until dotCount) {
                val a = i * 137.5
                val r = puckRadius * (0.15f + (i % 5) * 0.13f)
                val x = center.x + (r * kotlin.math.cos(Math.toRadians(a))).toFloat()
                val y = center.y + (r * kotlin.math.sin(Math.toRadians(a))).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = 1.2f,
                    center = Offset(x, y)
                )
            }

            if (state == PairingState.Paired) {
                drawCircle(
                    color = OnboardingTokens.Accent.copy(alpha = 0.15f),
                    radius = 12.dp.toPx(),
                    center = ledCenter
                )
                drawCircle(
                    color = OnboardingTokens.Accent.copy(alpha = 0.35f),
                    radius = 7.dp.toPx(),
                    center = ledCenter
                )
            }

            val ledColor = when (state) {
                PairingState.Paired -> OnboardingTokens.Accent
                else -> OnboardingTokens.LedDim
            }
            val ledRadius = if (state == PairingState.Paired) {
                4.dp.toPx() * ledPulse.value
            } else {
                4.dp.toPx()
            }
            drawCircle(color = ledColor, radius = ledRadius, center = ledCenter)
        }

        // Check badge — bottom-right relative to the puck center
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
            .background(color = OnboardingTokens.Accent, shape = CircleShape),
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
