package com.softwiredtech.dashpilot.ble

import android.content.Context
import com.softwiredtech.dashpilot.R
import com.softwiredtech.dashpilot.api.GitHubFirmwareException

internal fun Context.developmentFirmwareError(error: Exception): String {
    val github = error as? GitHubFirmwareException
    return when (github?.kind) {
        GitHubFirmwareException.Kind.AUTHENTICATION -> getString(R.string.fw_dev_auth_required)
        GitHubFirmwareException.Kind.RATE_LIMIT -> getString(R.string.fw_dev_rate_limit,
            github.retryAt ?: getString(R.string.fw_dev_retry_later))
        GitHubFirmwareException.Kind.MISSING -> getString(R.string.fw_dev_missing)
        GitHubFirmwareException.Kind.WORKFLOW_MISSING -> getString(R.string.fw_dev_workflow_missing)
        GitHubFirmwareException.Kind.STALE -> getString(R.string.fw_dev_stale)
        GitHubFirmwareException.Kind.INVALID -> getString(R.string.fw_dev_invalid)
        else -> getString(R.string.fw_dev_network)
    }
}
