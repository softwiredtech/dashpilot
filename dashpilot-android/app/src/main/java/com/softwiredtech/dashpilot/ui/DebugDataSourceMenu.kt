package com.softwiredtech.dashpilot.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.softwiredtech.dashpilot.BuildConfig
import com.softwiredtech.dashpilot.datamodel.dash.DASH_PREFS_NAME
import com.softwiredtech.dashpilot.datasource.ConnectionStatus
import com.softwiredtech.dashpilot.datasource.DataSourceType
import com.softwiredtech.dashpilot.ui.theme.DarkColors

private const val PREF_LAST_WEBSOCKET_ADDRESS = "last_websocket_address"

private val debugSources = buildList {
    add(DataSourceType.COMMA to "comma")
    add(DataSourceType.DASHKIT to "DashKit")
    if (BuildConfig.DEBUG) {
        add(DataSourceType.WEBSOCKET to "WebSocket")
    }
}

@Composable
fun DebugDataSourceMenu(
    connectionStatus: ConnectionStatus,
    onSelectDataSource: (String) -> Unit,
    onConnect: (serverAddress: String, dataSourceType: String) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    val idle = connectionStatus is ConnectionStatus.Disconnected ||
            connectionStatus is ConnectionStatus.Error

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = when (connectionStatus) {
                    is ConnectionStatus.Connected -> "Data source: connected"
                    is ConnectionStatus.Connecting -> "Data source: connecting…"
                    else -> "Data source"
                },
                color = DarkColors.TextMuted,
                fontSize = 13.sp
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = DarkColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (idle) {
                debugSources.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            if (key == DataSourceType.WEBSOCKET) {
                                showIpDialog = true
                            } else {
                                onSelectDataSource(key)
                                onConnect("", key)
                            }
                        }
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text("Disconnect") },
                    onClick = {
                        expanded = false
                        onDisconnect()
                    }
                )
            }
        }
        if (connectionStatus is ConnectionStatus.Error) {
            Text(
                text = connectionStatus.message,
                color = DarkColors.Error,
                fontSize = 12.sp
            )
        }
    }

    if (showIpDialog) {
        var address by remember { mutableStateOf(getLastWebsocketAddress(context)) }
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("WebSocket server") },
            text = {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("IP address") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = address.isNotBlank(),
                    onClick = {
                        showIpDialog = false
                        setLastWebsocketAddress(context, address)
                        onSelectDataSource(DataSourceType.WEBSOCKET)
                        onConnect(address.trim(), DataSourceType.WEBSOCKET)
                    }
                ) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun getLastWebsocketAddress(context: Context): String =
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(PREF_LAST_WEBSOCKET_ADDRESS, "") ?: ""

private fun setLastWebsocketAddress(context: Context, address: String) {
    context.getSharedPreferences(DASH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit { putString(PREF_LAST_WEBSOCKET_ADDRESS, address.trim()) }
}
