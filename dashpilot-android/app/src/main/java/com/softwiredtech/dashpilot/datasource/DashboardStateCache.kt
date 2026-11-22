package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.DashState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * One collector for a connection's combined dashboard data. Screens can leave
 * and return without restarting the pipeline or briefly rendering empty values.
 * The connection scope cancels this collector when the session is torn down.
 */
internal fun Flow<DashState>.cacheForConnection(
    scope: CoroutineScope,
    initial: DashState = DashState()
): StateFlow<DashState> = stateIn(scope, SharingStarted.Eagerly, initial)
