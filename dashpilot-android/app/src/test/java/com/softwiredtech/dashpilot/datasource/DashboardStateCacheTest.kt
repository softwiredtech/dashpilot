package com.softwiredtech.dashpilot.datasource

import com.softwiredtech.dashpilot.datamodel.dash.CarState
import com.softwiredtech.dashpilot.datamodel.dash.DashState
import com.softwiredtech.dashpilot.datamodel.dash.DisplaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardStateCacheTest {
    @Test fun returningToDriveGetsLatestReadingsWithoutWaitingForMoreVehicleTraffic() = runTest {
        val source = MutableSharedFlow<DashState>(extraBufferCapacity = 1)
        val cache = source.cacheForConnection(backgroundScope)
        runCurrent()
        val frame = DashState(carState = CarState(gear = 4f, odometer = 42000f, acTemp = 21f))
        assertTrue(source.tryEmit(frame))
        runCurrent()

        assertEquals(frame, cache.first()) // First visit to Drive.
        assertEquals(frame, cache.value) // The next composition reads synchronously.
        assertEquals(frame, cache.first()) // Return to Drive; no new frame emitted.
    }

    @Test fun switchingTabsDoesNotRestartTheUpstreamSubscription() = runTest {
        val source = MutableSharedFlow<DashState>(extraBufferCapacity = 1)
        var subscriptions = 0
        var completions = 0
        val cache = source.onStart { subscriptions++ }.onCompletion { completions++ }
            .cacheForConnection(backgroundScope)
        runCurrent()
        val drive = backgroundScope.launch { cache.collect() }
        val battery = backgroundScope.launch { cache.collect() }
        runCurrent()
        drive.cancel()
        battery.cancel()
        runCurrent()
        val returnToDrive = backgroundScope.launch { cache.collect() }
        runCurrent()
        assertEquals(1, subscriptions)
        assertEquals(0, completions)
        returnToDrive.cancel()
    }

    @Test fun latestReadingsStayCurrentWhileDriveIsOffScreen() = runTest {
        val source = MutableSharedFlow<DashState>(extraBufferCapacity = 1)
        val cache = source.cacheForConnection(backgroundScope)
        runCurrent()
        assertTrue(source.tryEmit(DashState(carState = CarState(odometer = 1f))))
        runCurrent()
        val latest = DashState(carState = CarState(odometer = 2f))
        assertTrue(source.tryEmit(latest))
        runCurrent()
        assertEquals(latest, cache.first())
    }

    @Test fun tearingDownTheConnectionStopsItsCacheAndNewSessionStartsFresh() = runTest {
        val source = MutableSharedFlow<DashState>(extraBufferCapacity = 1)
        var completions = 0
        val session = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val old = source.onCompletion { completions++ }.cacheForConnection(session)
        runCurrent()
        val oldFrame = DashState(carState = CarState(odometer = 100f))
        assertTrue(source.tryEmit(oldFrame))
        runCurrent()
        assertEquals(oldFrame, old.value)
        session.cancel()
        runCurrent()
        assertEquals(1, completions)

        val next = source.cacheForConnection(backgroundScope)
        assertEquals(DashState(), next.value)
    }

    @Test fun initialSnapshotUsesTheSessionsDisplaySettings() = runTest {
        val source = MutableSharedFlow<DashState>()
        val initial = DashState(displaySettings = DisplaySettings(useImperial = true), dataSourceType = "dashkit")
        val cache = source.cacheForConnection(backgroundScope, initial)
        assertEquals(initial, cache.value)
    }
}
