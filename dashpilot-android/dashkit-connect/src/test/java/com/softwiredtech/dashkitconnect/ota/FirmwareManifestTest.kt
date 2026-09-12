package com.softwiredtech.dashkitconnect.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirmwareManifestTest {
    @Test
    fun parses_full_manifest() {
        val m = FirmwareManifest.fromJson(
            """{"version":"1.2.3","url":"https://x/fw.bin","sha256":"abc","notes":"hi","minAppVersion":"0.7.0"}"""
        )!!
        assertEquals("1.2.3", m.version)
        assertEquals("https://x/fw.bin", m.url)
        assertEquals("abc", m.sha256)
        assertEquals("hi", m.notes)
        assertEquals("0.7.0", m.minAppVersion)
    }

    @Test
    fun optional_fields_default_to_null() {
        val m = FirmwareManifest.fromJson("""{"version":"1.2.3","url":"https://x/fw.bin","notes":null}""")!!
        assertNull(m.sha256)
        assertNull(m.notes)
        assertNull(m.minAppVersion)
    }

    @Test
    fun rejects_missing_required_fields_and_bad_json() {
        assertNull(FirmwareManifest.fromJson("""{"url":"https://x/fw.bin"}"""))
        assertNull(FirmwareManifest.fromJson("""{"version":"1.2.3"}"""))
        assertNull(FirmwareManifest.fromJson("not json"))
    }
}
