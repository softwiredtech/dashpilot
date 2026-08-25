package com.softwiredtech.dashpilot.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeslaClientProvisionTest {
    @Test
    fun builds_provision_payload() {
        val payload = TeslaClient.buildProvisionPayload(
            "5YJ3E7EB1MF123456",
            "AA:BB:CC:DD:EE:FF",
        )!!

        assertArrayEquals(
            byteArrayOf(TeslaClient.CMD_PROVISION.toByte()) +
                "5YJ3E7EB1MF123456".toByteArray() +
                byteArrayOf(0, 0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte(), 0xCC.toByte(), 0xBB.toByte(), 0xAA.toByte()),
            payload,
        )
    }

    @Test
    fun rejects_invalid_provision_values() {
        assertNull(TeslaClient.buildProvisionPayload("5YJ3", "AA:BB:CC:DD:EE:FF"))
        assertNull(TeslaClient.buildProvisionPayload("5YJ3E7EB1MF123456", "AA:BB:CC"))
    }
}
