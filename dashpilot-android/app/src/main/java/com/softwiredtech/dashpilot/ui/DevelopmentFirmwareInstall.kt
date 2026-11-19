package com.softwiredtech.dashpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.api.*
import com.softwiredtech.dashpilot.ble.developmentFirmwareError
import com.softwiredtech.dashpilot.ui.theme.DarkColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DevelopmentFirmwareInstall(
    enabled: Boolean,
    token: String,
    onTokenChanged: (String) -> Unit,
    onInstall: (VerifiedPrFirmware, String) -> Unit,
) {
    val repository = remember { DevelopmentFirmwareRepository() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var pulls by remember { mutableStateOf<List<FirmwarePullRequest>>(emptyList()) }
    var selected by remember { mutableStateOf<FirmwarePullRequest?>(null) }
    var selection by remember { mutableStateOf<PrBuildSelection?>(null) }
    var verified by remember { mutableStateOf<VerifiedPrFirmware?>(null) }
    var loading by remember { mutableStateOf(true) }
    var buildLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        selection = null
        verified = null
        try {
            pulls = repository.pulls(token)
            selected = pulls.firstOrNull { it.number == selected?.number } ?: pulls.firstOrNull()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            selected = null
            error = context.developmentFirmwareError(e)
        } finally { loading = false }
    }

    LaunchedEffect(selected, refresh, loading) {
        selection = null
        verified = null
        val pull = selected
        if (loading || pull == null) return@LaunchedEffect
        buildLoading = true
        error = null
        try { selection = repository.findBuild(pull, token)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = context.developmentFirmwareError(e)
        } finally { buildLoading = false }
    }

    val build = (selection as? PrBuildSelection.Ready)?.build
    val run = build?.run ?: (selection as? PrBuildSelection.Unavailable)?.run
    val busy = loading || buildLoading || progress != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.fw_dev_title), color = DarkColors.TextMuted, fontSize = 16.sp)
        Text(stringResource(R.string.fw_dev_auth_help), color = DarkColors.ContentDisabled, fontSize = 13.sp)
        OutlinedTextField(
            value = token, onValueChange = onTokenChanged,
            label = { Text(stringResource(R.string.fw_dev_token)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (loading || buildLoading) {
            Text(stringResource(R.string.fw_dev_loading), color = DarkColors.ContentDisabled)
        }
        if (!loading && pulls.isEmpty() && error == null) {
            Text(stringResource(R.string.fw_dev_empty), color = DarkColors.ContentDisabled)
        }
        if (pulls.isNotEmpty()) {
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(selected?.let { "#${it.number} — ${it.title}" } ?: stringResource(R.string.fw_dev_select))
                }
                DropdownMenu(expanded = expanded && !busy, onDismissRequest = { expanded = false }) {
                    pulls.forEach { pull ->
                        DropdownMenuItem(text = { Text("#${pull.number} — ${pull.title}") }, onClick = {
                            if (selected != pull) {
                                selected = pull
                                selection = null
                                verified = null
                            }
                            expanded = false
                        })
                    }
                }
            }
        }
        selected?.let { pull ->
            Text(stringResource(R.string.fw_dev_commit, pull.commit), color = DarkColors.ContentDisabled, fontSize = 13.sp)
        }
        run?.let {
            val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault()).format(build?.artifact?.createdAt ?: it.createdAt)
            Text(stringResource(R.string.fw_dev_date, date), color = DarkColors.ContentDisabled, fontSize = 13.sp)
        }
        (selection as? PrBuildSelection.Unavailable)?.let {
            val message = when (it.problem) {
                PrBuildProblem.PENDING -> R.string.fw_dev_pending
                PrBuildProblem.FAILED -> R.string.fw_dev_failed
                PrBuildProblem.MISSING -> R.string.fw_dev_missing
                PrBuildProblem.EXPIRED -> R.string.fw_dev_expired
            }
            Text(stringResource(message), color = DarkColors.ContentDisabled)
        }
        error?.let { Text(it, color = DarkColors.Error) }
        progress?.let {
            Text(stringResource(R.string.settings_firmware_downloading, (it * 100).toInt()), color = DarkColors.TextMuted)
            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
        }
        if (build != null && verified == null) {
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    error = null
                    progress = 0f
                    try { verified = repository.download(build, token) { progress = it }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { error = context.developmentFirmwareError(e)
                    } finally { progress = null }
                }
            }) { Text(stringResource(R.string.fw_dev_download)) }
        }
        verified?.let { firmware ->
            Text(stringResource(R.string.fw_dev_version, firmware.version), color = DarkColors.TextMuted)
            Button(enabled = enabled && !busy, modifier = Modifier.fillMaxWidth(),
                onClick = { confirming = true }) { Text(stringResource(R.string.fw_dev_install)) }
        }
        TextButton(onClick = { refresh++ }, enabled = !busy) {
            Text(stringResource(R.string.fw_releases_refresh))
        }
        TextButton(onClick = { uriHandler.openUri(run?.url ?: DevelopmentFirmwareRepository.WORKFLOW_URL) }) {
            Text(stringResource(R.string.fw_dev_open_github))
        }
    }

    verified?.let { firmware ->
        if (confirming) AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.fw_dev_confirm_title)) },
            text = { Text(stringResource(R.string.fw_dev_confirm_body, firmware.build.pull.number,
                firmware.version, firmware.build.pull.commit)) },
            confirmButton = {
                TextButton(enabled = enabled, onClick = {
                    confirming = false
                    onInstall(firmware, token)
                }) { Text(stringResource(R.string.settings_firmware_file_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.settings_firmware_file_dialog_cancel))
                }
            },
        )
    }
}
