package com.softwiredtech.dashpilot.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.ui.theme.AccentColor

private data class DataSource(
    val key: String,
    val label: String,
    val icon: ImageVector?,
)

private val dataSources = buildList {
    add(DataSource("comma", "comma", null))
    if (BuildConfig.DEBUG) {
        add(DataSource("ble", "BLE", Icons.Rounded.Bluetooth))
        add(DataSource("websocket", "WebSocket", Icons.Rounded.Lan))
    }
}

@Composable
fun SetupScreen(
    connectionStatus: ConnectionStatus,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit,
    onNext: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("dash_prefs", Context.MODE_PRIVATE) }
    var serverAddress by rememberSaveable { mutableStateOf(sharedPrefs.getString("device_ip", "192.168.1.105") ?: "192.168.1.105") }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentSource = dataSources[selectedIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (BuildConfig.DEBUG) stringResource(R.string.setup_select_data_source) else stringResource(R.string.setup_connect_your_comma_device),
                color = Color(0xFF888888),
                fontSize = 14.sp
            )
        }

        ConnectionVisualization(
            connectionStatus = connectionStatus,
            currentSource = currentSource,
            selectedIndex = selectedIndex,
            onSourceSelected = { selectedIndex = it }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val showIpField = currentSource.key in listOf("comma", "websocket")
            OutlinedTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                label = { Text(stringResource(R.string.connection_device_ip)) },
                singleLine = true,
                enabled = showIpField && connectionStatus is ConnectionStatus.Disconnected,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedBorderColor = Color.White,
                    unfocusedLabelColor = Color(0xFF888888),
                    focusedLabelColor = Color.White,
                    cursorColor = Color.White,
                    disabledTextColor = Color(0xFF888888),
                    disabledBorderColor = Color(0xFF333333),
                    disabledLabelColor = Color(0xFF666666)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .alpha(if (showIpField) 1f else 0f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (connectionStatus) {
                is ConnectionStatus.Disconnected -> {
                    Button(
                        onClick = {
                            onConnect(serverAddress, currentSource.key)
                            sharedPrefs.edit { putString("device_ip", serverAddress) }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.setup_connect),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is ConnectionStatus.Connecting, is ConnectionStatus.Connected -> {
                    val isConnecting = connectionStatus is ConnectionStatus.Connecting

                    OutlinedButton(
                        onClick = { onDisconnect() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (isConnecting) R.string.setup_cancel
                                else R.string.setup_disconnect
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onNext() },
                        enabled = !isConnecting,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF555555),
                            disabledContentColor = Color(0xFFAAAAAA)
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (isConnecting) R.string.setup_connecting
                                else R.string.setup_next
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = Color(0xFF888888),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ConnectionVisualization(
    connectionStatus: ConnectionStatus,
    currentSource: DataSource,
    selectedIndex: Int,
    onSourceSelected: (Int) -> Unit
) {
    val isConnected = connectionStatus is ConnectionStatus.Connected
    val isDisconnected = connectionStatus is ConnectionStatus.Disconnected
    val dashLineColor = if (isConnected) AccentColor else Color(0xFF555555)
    val animating = !isDisconnected

    val infiniteTransition = rememberInfiniteTransition(label = "dashFlow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    val circleBorderModifier = if (isConnected) {
        Modifier.border(2.dp, AccentColor, CircleShape)
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .then(circleBorderModifier)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DirectionsCar,
                contentDescription = "Vehicle",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        AnimatedDashedLine(phase = phase, animating = animating, color = dashLineColor)

        if (BuildConfig.DEBUG && dataSources.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    DataSourceIcon(currentSource, size = 18, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = currentSource.label)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    dataSources.forEachIndexed { index, source ->
                        DropdownMenuItem(
                            text = { Text(source.label) },
                            leadingIcon = { DataSourceIcon(source, size = 18, tint = Color.Black) },
                            onClick = {
                                onSourceSelected(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    DataSourceIcon(currentSource, size = 24, tint = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentSource.label,
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
            }
        }

        AnimatedDashedLine(phase = phase, animating = animating, color = dashLineColor)

        Box(
            modifier = Modifier
                .size(80.dp)
                .then(circleBorderModifier)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PhoneAndroid,
                contentDescription = "Phone",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun DataSourceIcon(source: DataSource, size: Int, tint: Color = Color.Unspecified) {
    Icon(
        imageVector = source.icon ?: ImageVector.vectorResource(R.drawable.ic_comma),
        contentDescription = source.label,
        tint = tint,
        modifier = Modifier.size(size.dp)
    )
}

@Composable
private fun AnimatedDashedLine(phase: Float, animating: Boolean, color: Color) {
    Canvas(
        modifier = Modifier
            .width(4.dp)
            .height(64.dp)
            .padding(vertical = 4.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(10f, 10f),
                phase = if (animating) -phase else 0f
            )
        )
    }
}
