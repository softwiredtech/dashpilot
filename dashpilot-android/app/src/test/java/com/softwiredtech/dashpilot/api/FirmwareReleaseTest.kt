package com.softwiredtech.dashpilot.api

import org.junit.Assert.*
import org.junit.Test

class FirmwareReleaseTest {
    private val digest = "a".repeat(64)
    private val url = "https://github.com/softwiredtech/dashkit-firmware/releases/download/0.1.0/dashkit-firmware.bin"
    private val asset = """{"name":"dashkit-firmware.bin","size":666192,"browser_download_url":"$url","digest":"sha256:$digest"}"""

    @Test fun selects_ota_image_and_digest_with_usb_asset_first() {
        val usb = asset.replace("dashkit-firmware.bin", "dashkit-firmware-usb.bin")
        val releases = FirmwareUpdateRepository.parseReleases(
            """[{"tag_name":"0.1.0","name":"0.1.0","prerelease":false,"draft":false,"assets":[$usb,$asset]}]"""
        )
        val release = releases.single()
        assertEquals("0.1.0", release.label)
        assertEquals(url, release.manifest.url)
        assertEquals(digest, release.manifest.sha256)
    }

    @Test fun ignores_drafts_usb_only_and_empty_assets() {
        val usb = asset.replace("dashkit-firmware.bin", "dashkit-firmware-usb.bin")
        val releases = FirmwareUpdateRepository.parseReleases(
            """[
                {"tag_name":"draft","draft":true,"assets":[$asset]},
                {"tag_name":"usb-only","assets":[$usb]},
                {"tag_name":"empty","assets":[]},
                {"tag_name":"zero-size","assets":[${asset.replace("666192", "0")}]}
            ]"""
        )
        assertTrue(releases.isEmpty())
    }

    @Test fun supports_prereleases_and_optional_digest_and_name() {
        val noDigest = asset.replace("\"sha256:$digest\"", "null")
        val release = FirmwareUpdateRepository.parseReleases(
            """[{"tag_name":"0.1.0","name":"Preview","prerelease":true,"assets":[$noDigest]}]"""
        ).single()
        assertTrue(release.prerelease)
        assertEquals("0.1.0 — Preview", release.label)
        assertNull(release.manifest.sha256)
        val unnamed = FirmwareUpdateRepository.parseReleases(
            """[{"tag_name":"0.1.0","name":null,"assets":[$asset]}]"""
        ).single()
        assertEquals("0.1.0", unnamed.label)
    }

    @Test fun rejects_asset_urls_outside_the_firmware_repository() {
        val other = asset.replace("softwiredtech/dashkit-firmware", "another/repository")
        assertTrue(FirmwareUpdateRepository.parseReleases(
            """[{"tag_name":"0.1.0","assets":[$other]}]"""
        ).isEmpty())
    }
}
