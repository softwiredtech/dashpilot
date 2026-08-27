package com.softwiredtech.dashpilot.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeslaVehicleScannerTest {
    private val vin = "5YJ3E7EB1MF123456"

    @Test
    fun matches_legacy_advert_roles() {
        for (role in "CRDP") {
            assertTrue(TeslaVehicleScanner.matchesVin("Sb753e5f4c0736ab4$role", vin))
        }
        assertFalse(TeslaVehicleScanner.matchesVin("Sb753e5f4c0736ab4X", vin))
    }

    @Test
    fun matches_modern_vin_tails() {
        assertTrue(TeslaVehicleScanner.matchesVin("Tesla 3456", vin))
        assertTrue(TeslaVehicleScanner.matchesVin("Tesla 23456", vin))
        assertTrue(TeslaVehicleScanner.matchesVin("Tesla 123456", vin))
        assertFalse(TeslaVehicleScanner.matchesVin("Tesla 654321", vin))
    }
}
