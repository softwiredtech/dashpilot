package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.api.FirmwareRelease
import com.softwiredtech.dashpilot.api.FirmwareUpdateRepository
import com.softwiredtech.dashpilot.ui.theme.AccentColor
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import kotlinx.coroutines.CancellationException

@Composable
fun FirmwareReleaseInstall(enabled: Boolean, onInstall: (FirmwareRelease) -> Unit) {
    var releases by remember { mutableStateOf<List<FirmwareRelease>>(emptyList()) }
    var selected by remember { mutableStateOf<FirmwareRelease?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<FirmwareRelease?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            releases = FirmwareUpdateRepository.fetchReleases()
            selected = releases.firstOrNull { it.tag == selected?.tag } ?: releases.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.localizedMessage ?: e.toString()
        } finally {
            loading = false
        }
    }

    Column {
        Text(stringResource(R.string.fw_releases_source), color = DarkColors.ContentDisabled, fontSize = 13.sp)
        when {
            loading -> Text(stringResource(R.string.fw_releases_loading), color = DarkColors.TextMuted)
            error != null -> Text(stringResource(R.string.fw_releases_error, error.orEmpty()), color = DarkColors.Error)
            releases.isEmpty() -> Text(stringResource(R.string.fw_releases_empty), color = DarkColors.TextMuted)
            else -> {
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.fw_releases_selection,
                            selected?.let { releaseLabel(it) }.orEmpty()))
                    }
                    DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                        releases.forEach { release ->
                            DropdownMenuItem(
                                text = { Text(releaseLabel(release)) },
                                onClick = { selected = release; expanded = false },
                            )
                        }
                    }
                }
                Button(
                    onClick = { confirming = selected },
                    enabled = enabled && selected != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                ) {
                    Text(stringResource(R.string.fw_releases_install))
                }
            }
        }
        TextButton(onClick = { refresh++ }, enabled = enabled && !loading) {
            Text(stringResource(R.string.fw_releases_refresh))
        }
    }

    confirming?.let { release ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.fw_releases_confirm_title)) },
            text = { Text(stringResource(R.string.fw_releases_confirm_body, releaseLabel(release))) },
            confirmButton = {
                TextButton(enabled = enabled, onClick = {
                    confirming = null
                    onInstall(release)
                }) { Text(stringResource(R.string.settings_firmware_file_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.settings_firmware_file_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun releaseLabel(release: FirmwareRelease): String =
    if (release.prerelease) stringResource(R.string.fw_releases_prerelease, release.label) else release.label
