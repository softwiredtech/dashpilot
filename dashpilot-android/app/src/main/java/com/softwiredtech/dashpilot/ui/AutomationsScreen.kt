package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors

/** A three-finger-press option, bound to a vehicle control id. */
private data class ThreeFingerOption(
    val id: String,
    val icon: ImageVector,
    val title: String
)

private val threeFingerOptions = listOf(
    ThreeFingerOption("glovebox", Icons.Rounded.Inbox, "Open Glovebox"),
    ThreeFingerOption("battery_preheat", Icons.Rounded.Thermostat, "Preheat Battery")
)

/**
 * Automations screen. Lets the user enable the wiper-off automation and bind a
 * single infotainment three-finger-press gesture to one vehicle action (the
 * options are mutually exclusive). Firmware support is not wired up yet.
 */
@Composable
fun AutomationsScreen(
    wiperOffEnabled: Boolean,
    onWiperOffChange: (Boolean) -> Unit,
    threeFingerActionId: String?,
    onToggleThreeFinger: (String) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Automations",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel("Wipers")
            Spacer(modifier = Modifier.height(8.dp))
            AutomationRow(
                icon = Icons.Rounded.WaterDrop,
                title = "Wiper Off",
                subtitle = "Keep wipers disabled automatically",
                checked = wiperOffEnabled,
                onToggle = { onWiperOffChange(!wiperOffEnabled) }
            )

            Spacer(modifier = Modifier.height(28.dp))

            SectionLabel("Three-finger press")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Bind the infotainment three-finger press to one action",
                color = DarkColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            threeFingerOptions.forEachIndexed { index, option ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                AutomationRow(
                    icon = option.icon,
                    title = option.title,
                    subtitle = null,
                    checked = option.id == threeFingerActionId,
                    onToggle = { onToggleThreeFinger(option.id) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = DarkColors.TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun AutomationRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkColors.Surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) AccentColor else Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = DarkColors.TextMuted,
                    fontSize = 13.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = DarkColors.Disabled
            )
        )
    }
}
