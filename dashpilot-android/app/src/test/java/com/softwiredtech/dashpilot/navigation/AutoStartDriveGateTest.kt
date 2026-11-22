package com.softwiredtech.dashpilot.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartDriveGateTest {
    private fun AutoStartDriveGate.start(
        gear: Float? = 4f,
        enabled: Boolean = true,
        connected: Boolean = true,
        canNavigate: Boolean = true,
        dashboardVisible: Boolean = false,
        previewVisible: Boolean = false
    ) = shouldStart(enabled, connected, gear, canNavigate, dashboardVisible, previewVisible)

    @Test fun parkAndUnknownGearNeverStartDrive() {
        val gate = AutoStartDriveGate()
        for (gear in listOf(null, 0f, 1f, 5f, Float.NaN)) assertFalse(gate.start(gear))
    }

    @Test fun everyKnownNonParkGearStartsDrive() {
        for (gear in listOf(2f, 3f, 4f)) assertTrue(AutoStartDriveGate().start(gear))
    }

    @Test fun disabledOrDisconnectedNeverStartDrive() {
        assertFalse(AutoStartDriveGate().start(enabled = false))
        assertFalse(AutoStartDriveGate().start(connected = false))
    }

    @Test fun onlyStartsOnceUntilParkEvenWhenGearChangesOrDataIsUnknown() {
        val gate = AutoStartDriveGate()
        assertTrue(gate.start(2f))
        for (gear in listOf(2f, 3f, 4f, 0f, null, 4f)) assertFalse(gate.start(gear))
        assertFalse(gate.start(1f))
        assertTrue(gate.start(4f))
    }

    @Test fun backgroundOrOnboardingDefersLaunchUntilNavigationIsAvailable() {
        val gate = AutoStartDriveGate()
        assertFalse(gate.start(canNavigate = false))
        assertTrue(gate.start(canNavigate = true))
    }

    @Test fun manuallyOpenedDashboardConsumesLaunchSoExitingDoesNotReopenIt() {
        val gate = AutoStartDriveGate()
        assertFalse(gate.start(dashboardVisible = true))
        assertFalse(gate.start(dashboardVisible = false))
    }

    @Test fun reconnectingWhileOutOfParkStartsNewSession() {
        val gate = AutoStartDriveGate()
        assertTrue(gate.start())
        assertFalse(gate.start(connected = false))
        assertTrue(gate.start())
    }

    @Test fun enablingWhileAlreadyOutOfParkStartsDrive() {
        val gate = AutoStartDriveGate()
        assertFalse(gate.start(enabled = false))
        assertTrue(gate.start(enabled = true))
    }

    @Test fun previewDefersDepartureUntilDismissedWithoutConsumingIt() {
        val gate = AutoStartDriveGate()
        assertFalse(gate.start(1f, previewVisible = true))
        for (gear in listOf(2f, 3f, 4f)) {
            assertFalse(gate.start(gear, previewVisible = true))
        }
        assertTrue(gate.start(previewVisible = false))
        assertFalse(gate.start())
    }

    @Test fun previewDoesNotCountAsAnAlreadyOpenLiveDashboard() {
        val gate = AutoStartDriveGate()
        assertFalse(gate.start(dashboardVisible = true, previewVisible = true))
        assertTrue(gate.start())
    }

    @Test fun repeatedPreviewsWhileParkedDoNotPreventLaterDeparture() {
        val gate = AutoStartDriveGate()
        repeat(3) {
            assertFalse(gate.start(1f, previewVisible = true))
            assertFalse(gate.start(1f))
        }
        assertTrue(gate.start(4f))
    }

    @Test fun previewDoesNotRearmAnAlreadyHandledDeparture() {
        val gate = AutoStartDriveGate()
        assertTrue(gate.start())
        assertFalse(gate.start(previewVisible = true))
        assertFalse(gate.start())
        assertFalse(gate.start(1f, previewVisible = true))
        assertFalse(gate.start(4f, previewVisible = true))
        assertTrue(gate.start())
    }

    @Test fun reconnectAndEnableDuringPreviewRemainPending() {
        val gate = AutoStartDriveGate()
        assertTrue(gate.start())
        assertFalse(gate.start(connected = false, previewVisible = true))
        assertFalse(gate.start(previewVisible = true))
        assertTrue(gate.start())
        assertFalse(gate.start(enabled = false, previewVisible = true))
        assertFalse(gate.start(previewVisible = true))
        assertTrue(gate.start())
    }

    @Test fun closingPreviewInBackgroundWaitsUntilForegrounded() {
        val gate = AutoStartDriveGate()
        assertFalse(gate.start(previewVisible = true))
        assertFalse(gate.start(canNavigate = false))
        assertTrue(gate.start())
    }

    @Test fun returningToParkDuringPreviewCancelsPendingDeparture() {
        val gate = AutoStartDriveGate()
        assertFalse(gate.start(previewVisible = true))
        assertFalse(gate.start(1f, previewVisible = true))
        assertFalse(gate.start(1f))
        assertTrue(gate.start(2f))
    }
}
