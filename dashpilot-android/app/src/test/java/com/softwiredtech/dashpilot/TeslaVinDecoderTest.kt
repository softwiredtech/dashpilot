package com.softwiredtech.dashpilot

import com.softwiredtech.dashpilot.ble.TeslaVinDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeslaVinDecoderTest {

    // Real-world Tesla VIN structures (fictional serials, valid positioned
    // model/year/plant codes). VIN position 4 encodes the model letter, 10 the
    // year, 11 the plant.
    private val model3Fremont = "5YJ3E7EB1MF123456" // M -> 2021, F -> Fremont
    private val modelYShanghai = "LRWYG7EKXNG123456" // N -> 2022, G -> Shanghai
    private val modelSFremont = "5YJSA1EP1RF654321" // R -> 2024, F -> Fremont

    @Test
    fun decodesValidTeslaVin() {
        val d = TeslaVinDecoder.decode(model3Fremont)
        assertEquals(true, d.valid)
        assertEquals("Tesla", d.manufacturer)
        assertEquals("Model 3", d.model)
        assertEquals("Dual Motor", d.drive)
        assertEquals("Sedan LHD", d.body)
        assertEquals("Li-Ion", d.battery)
        assertEquals(2021, d.modelYear)
        assertEquals("Fremont, CA", d.plant)
    }

    @Test
    fun decodesMarketModels() {
        assertEquals("Model Y", TeslaVinDecoder.decode(modelYShanghai).model)
        assertEquals("Model S", TeslaVinDecoder.decode(modelSFremont).model)
        assertEquals(2022, TeslaVinDecoder.decode(modelYShanghai).modelYear)
        assertEquals("Dual Motor", TeslaVinDecoder.decode(modelYShanghai).drive)
    }

    @Test
    fun rejectsShortOrInvalidVin() {
        assertFalse(TeslaVinDecoder.decode("5YJ3E7EB1M").valid)
        assertFalse(TeslaVinDecoder.decode("5YJ3E7EB1MF12345").valid)
        assertTrue(TeslaVinDecoder.decode("5YJ3E7EB1MF123456").valid)
    }

    @Test
    fun rejectsIllegalCharacters() {
        val bad = "5YJ3E7EB1MFI23456" // I/O/Q not allowed in VIN charset
        assertFalse(TeslaVinDecoder.decode(bad).valid)
    }

    @Test
    fun rejectsNonTeslaVin() {
        assertFalse(TeslaVinDecoder.decode("1HGCM82633A004352").valid)
        assertFalse(TeslaVinDecoder.isTeslaVin("1HGCM82633A004352"))
    }

    @Test
    fun descriptiveComposes() {
        assertEquals(
            "Tesla · Model 3 · Dual Motor · 2021 · Fremont, CA",
            TeslaVinDecoder.descriptive(TeslaVinDecoder.decode(model3Fremont)),
        )
        assertEquals(
            "Tesla · Model Y · Dual Motor · 2022 · Shanghai, China",
            TeslaVinDecoder.descriptive(TeslaVinDecoder.decode(modelYShanghai)),
        )
    }

    @Test
    fun detailLineComposes() {
        val d = TeslaVinDecoder.decode(model3Fremont)
        assertEquals("Sedan LHD · Li-Ion · Dual Motor", TeslaVinDecoder.detailLine(d))
    }

    @Test
    fun supportsFauxAdvertNameMatching() {
        // The modern advert is "Tesla " + last6 of the VIN; the derived names
        // helper must accept that as the derived name for the same VIN.
        val vin = model3Fremont
        val derived = com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.derivedNames(vin)
        assertTrue("Tesla " + vin.takeLast(6) in derived)
        assertTrue(derived.any { it.startsWith("S") && it.length == 18 })
    }

    @Test
    fun matchesModernAdvertTailsFromFourToSixCharacters() {
        val vin = model3Fremont
        assertTrue(com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.matchesVin("Tesla " + vin.takeLast(4), vin))
        assertTrue(com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.matchesVin("Tesla " + vin.takeLast(5), vin))
        assertTrue(com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.matchesVin("Tesla " + vin.takeLast(6), vin))
        assertFalse(com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.matchesVin("Tesla 999999", vin))
    }

    @Test
    fun matchesLegacyHashCaseInsensitivelyAndIgnoresRole() {
        val vin = model3Fremont
        val legacy = com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.derivedNames(vin)
            .first { it.startsWith("S") }
        assertTrue(com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.matchesVin(legacy, vin))
        assertTrue(com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.matchesVin(legacy.dropLast(1).uppercase() + "R", vin))
        assertFalse(com.softwiredtech.dashpilot.ble.TeslaVehicleScanner.matchesVin(legacy, modelYShanghai))
    }

    @Test
    fun checkDigitAcceptsSolvedAndXCaseVins() {
        // Check digit solved from the ISO 3779 table for this prefix/serial.
        assertTrue(TeslaVinDecoder.checkDigitValid("5YJ3E1EA8KF000001"))
        // Sum mod 11 == 10 -> the check character is 'X'.
        assertTrue(TeslaVinDecoder.checkDigitValid("5YJ3E1EAXKF000002"))
        // Input is normalised before validation.
        assertTrue(TeslaVinDecoder.checkDigitValid("5yj3e1ea8kf000001"))
        // Classic sanity vector: all ones passes.
        assertTrue(TeslaVinDecoder.checkDigitValid("11111111111111111"))
    }

    @Test
    fun checkDigitRejectsWrongDigitMalformedAndFictionalFixtures() {
        // Same frame with one wrong position-9 character.
        assertFalse(TeslaVinDecoder.checkDigitValid("5YJ3E1EA7KF000001"))
        assertFalse(TeslaVinDecoder.checkDigitValid(""))
        // The fictional test fixtures above carry arbitrary serials and do NOT
        // satisfy the check digit — proof that passing decode != verified VIN.
        assertFalse(TeslaVinDecoder.checkDigitValid(model3Fremont))
        assertFalse(TeslaVinDecoder.checkDigitValid(modelYShanghai))
    }
}
