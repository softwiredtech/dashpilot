package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(
    onLaunch: (dashboardType: String, serverAddress: String, dataSourceType: String) -> Unit
) {
    var serverAddress by rememberSaveable { mutableStateOf("192.168.1.105") }
    var dashboardType by rememberSaveable { mutableStateOf("web") }
    var dataSourceType by rememberSaveable { mutableStateOf("comma") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DashPilot Setup",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            label = { Text("Server IP Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Dashboard Type",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            FilterChip(
                selected = dashboardType == "web",
                onClick = { dashboardType = "web" },
                label = { Text("Web Dashboard") }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilterChip(
                selected = dashboardType == "rive",
                onClick = { dashboardType = "rive" },
                label = { Text("Rive Dashboard") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Data Source",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            FilterChip(
                selected = dataSourceType == "comma",
                onClick = { dataSourceType = "comma" },
                label = { Text("Comma") }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilterChip(
                selected = dataSourceType == "ble",
                onClick = { dataSourceType = "ble" },
                label = { Text("BLE") }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { onLaunch(dashboardType, serverAddress, dataSourceType) }) {
            Text("Launch Dashboard")
        }
    }
}
