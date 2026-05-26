package com.softwiredtech.dashpilot.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InfoPage(
    page: OnboardingPage.Info,
    onAdvance: () -> Unit
) {
    OnboardingPageScaffold(
        title = page.title,
        subtitle = page.subtitle,
        hero = page.media?.let { media ->
            {
                when (media) {
                    is MediaSpec.Image -> Image(
                        painter = painterResource(id = media.resId),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(media.heightDp.dp)
                    )
                    is MediaSpec.DevicePuck -> DevicePuck(
                        state = PairingState.Idle,
                        modifier = Modifier.size(media.heightDp.dp)
                    )
                }
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
