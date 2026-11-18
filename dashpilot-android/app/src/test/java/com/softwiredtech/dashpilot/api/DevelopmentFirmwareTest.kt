package com.softwiredtech.dashpilot.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DevelopmentFirmwareTest {
    private val sha = "1234567" + "a".repeat(33)
    private val pull = FirmwarePullRequest(17, "Ambient lights", sha)
    private val date = Instant.parse("2026-11-18T10:00:00Z")
    private val run = FirmwarePrRun(100, sha, "completed", "success", date, 1, listOf(17))
    private val artifact = FirmwarePrArtifact(200, "dashkit-pr-17-1234567", false, date.plusSeconds(86400), date)
    private val build = FirmwarePrBuild(pull, run, artifact)
    private val image = byteArrayOf(0xE9.toByte(), 1, 2, 3)
    private val usb = byteArrayOf(0xE9.toByte(), 9, 9, 9)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private val info get() = """{"pull_request":17,"commit":"$sha","version":"pr-17-1234567","target":"esp32s3","run_url":"${run.url}"}"""
    private fun files() = linkedMapOf(
        "dashkit-firmware-usb.bin" to usb,
        "dashkit-firmware.bin" to image,
        "SHA256SUMS.txt" to "${hash(usb)}  ./dashkit-firmware-usb.bin\n${hash(image)}  ./dashkit-firmware.bin\n".toByteArray(),
        "build-info.json" to info.toByteArray(),
    )
    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out -> files.forEach { (name, data) ->
            out.putNextEntry(ZipEntry(name)); out.write(data); out.closeEntry()
        } }
        return bytes.toByteArray()
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected firmware validation failure")
        } catch (e: GitHubFirmwareException) { assertEquals(GitHubFirmwareException.Kind.INVALID, e.kind) }
    }

    @Test fun selects_latest_success_only_for_current_head_and_correct_pr_workflow() {
        val newer = run.copy(id = 101, createdAt = date.plusSeconds(30))
        val runs = listOf(run, newer, newer.copy(id = 102, commit = "b".repeat(40)),
            newer.copy(id = 103, pulls = listOf(99)), newer.copy(id = 104, event = "push"),
            newer.copy(id = 105, path = ".github/workflows/release.yml"),
            newer.copy(id = 106, status = "in_progress", conclusion = null))
        assertEquals(newer, DevelopmentFirmwareRepository.latestSuccessfulRun(pull, runs))
        assertNull(DevelopmentFirmwareRepository.latestSuccessfulRun(pull.copy(commit = "c".repeat(40)), runs))
    }

    @Test fun empty_github_pr_association_still_requires_exact_head_and_archive_metadata() {
        assertEquals(run.copy(pulls = emptyList()), DevelopmentFirmwareRepository.latestSuccessfulRun(
            pull, listOf(run.copy(pulls = emptyList()))))
    }

    @Test fun selects_newest_run_id_when_timestamps_have_equal_second_precision() {
        val newer = run.copy(id = 101)
        assertEquals(newer, DevelopmentFirmwareRepository.latestSuccessfulRun(pull, listOf(run, newer)))
    }

    @Test fun artifact_selection_rejects_old_commit_wrong_pr_and_expiry() {
        fun problem(items: List<FirmwarePrArtifact>) = (DevelopmentFirmwareRepository.selectArtifact(
            pull, run, items, date) as PrBuildSelection.Unavailable).problem
        assertEquals(PrBuildProblem.MISSING, problem(listOf(artifact.copy(name = "dashkit-pr-17-abcdef0"))))
        assertEquals(PrBuildProblem.MISSING, problem(listOf(artifact.copy(name = "dashkit-pr-99-1234567"))))
        assertEquals(PrBuildProblem.EXPIRED, problem(listOf(artifact.copy(expired = true))))
        assertEquals(PrBuildProblem.EXPIRED, problem(listOf(artifact.copy(expiresAt = date))))
        assertEquals(PrBuildSelection.Ready(build), DevelopmentFirmwareRepository.selectArtifact(pull, run,
            listOf(artifact), date))
    }

    @Test fun extracts_app_binary_even_when_usb_is_first_and_verifies_metadata() {
        val verified = DevelopmentFirmwareRepository.verifyArchive(zip(files()), build)
        assertArrayEquals(image, verified.bytes)
        assertEquals("pr-17-1234567", verified.version)
    }

    @Test fun usb_only_archive_is_never_accepted_for_ota() {
        rejected { DevelopmentFirmwareRepository.verifyArchive(zip(files().apply { remove("dashkit-firmware.bin") }), build) }
    }

    @Test fun checksum_mismatch_or_missing_app_checksum_is_rejected() {
        val corrupt = files().apply { put("dashkit-firmware.bin", usb) }
        rejected { DevelopmentFirmwareRepository.verifyArchive(zip(corrupt), build) }
        val noAppHash = files().apply { put("SHA256SUMS.txt", "${hash(usb)}  dashkit-firmware-usb.bin".toByteArray()) }
        rejected { DevelopmentFirmwareRepository.verifyArchive(zip(noAppHash), build) }
    }

    @Test fun rejects_mismatched_pr_commit_target_run_and_version() {
        val badInfo = listOf(info.replace("\"pull_request\":17", "\"pull_request\":18"),
            info.replace(sha, "a".repeat(40)), info.replace("esp32s3", "esp32"),
            info.replace("runs/100", "runs/101"), info.replace("pr-17-1234567", "pr-17-abcdef0"), "{}")
        badInfo.forEach { json -> rejected {
            DevelopmentFirmwareRepository.verifyArchive(zip(files().apply { put("build-info.json", json.toByteArray()) }), build)
        } }
    }

    @Test fun rejects_traversal_and_oversized_uncompressed_entries() {
        rejected { DevelopmentFirmwareRepository.verifyArchive(zip(files().apply { put("../escape", image) }), build) }
        rejected { DevelopmentFirmwareRepository.verifyArchive(zip(files().apply {
            put("dashkit-firmware.bin", ByteArray(8 * 1024 * 1024 + 1))
        }), build) }
    }

    private fun client(respond: (Request) -> Response) = OkHttpClient.Builder().followRedirects(false)
        .addInterceptor { respond(it.request()) }.build()
    private fun response(request: Request, body: String, code: Int = 200, headers: Map<String, String> = emptyMap()) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
            .body(body.toResponseBody()).apply { headers.forEach { (key, value) -> header(key, value) } }.build()
    private val pullJson get() = """{"number":17,"title":"Ambient lights","state":"open","head":{"sha":"$sha"}}"""
    private val artifactJson get() = """{"id":200,"name":"dashkit-pr-17-1234567","expired":false,"expires_at":"${Instant.now().plusSeconds(86400)}","created_at":"$date"}"""
    private fun runJson(status: String, conclusion: String) = """{"id":100,"head_sha":"$sha","status":"$status","conclusion":$conclusion,"created_at":"$date","run_attempt":1,"pull_requests":[{"number":17}],"event":"pull_request","path":".github/workflows/pr-firmware.yml"}"""

    @Test fun reports_pending_failed_and_missing_builds_without_falling_back_to_old_commits() = runTest {
        for ((json, expected) in listOf(
            runJson("in_progress", "null") to PrBuildProblem.PENDING,
            runJson("completed", "\"failure\"") to PrBuildProblem.FAILED,
            runJson("completed", "\"success\"") to PrBuildProblem.MISSING,
            runJson("completed", "\"success\"").replace(sha, "b".repeat(40)) to PrBuildProblem.PENDING,
        )) {
            val repository = DevelopmentFirmwareRepository(client { request ->
                when {
                    request.url.encodedPath.contains("/pulls/") -> response(request, pullJson)
                    request.url.encodedPath.endsWith("/artifacts") -> response(request, """{"artifacts":[]}""")
                    else -> {
                        assertEquals(sha, request.url.queryParameter("head_sha"))
                        response(request, """{"workflow_runs":[$json]}""")
                    }
                }
            })
            assertEquals(expected, (repository.findBuild(pull, "") as PrBuildSelection.Unavailable).problem)
        }
    }

    @Test fun expired_artifact_is_rechecked_before_install() = runTest {
        val repository = DevelopmentFirmwareRepository(client { request ->
            response(request, if (request.url.encodedPath.contains("/pulls/")) pullJson
                else artifactJson.replace("\"expired\":false", "\"expired\":true"))
        })
        try { repository.ensureCurrent(build, ""); fail("Expected expired artifact failure")
        } catch (e: GitHubFirmwareException) { assertEquals(GitHubFirmwareException.Kind.MISSING, e.kind) }
    }

    @Test fun missing_upstream_workflow_is_explained_without_offering_other_builds() = runTest {
        val repository = DevelopmentFirmwareRepository(client { request ->
            assertEquals("api.github.com", request.url.host)
            assertTrue(request.url.encodedPath.startsWith("/repos/softwiredtech/dashkit-firmware/"))
            if (request.url.encodedPath.contains("/pulls/")) response(request, pullJson)
            else response(request, "", 404)
        })
        try { repository.findBuild(pull, ""); fail("Expected unavailable workflow")
        } catch (e: GitHubFirmwareException) {
            assertEquals(GitHubFirmwareException.Kind.WORKFLOW_MISSING, e.kind)
        }
    }

    @Test fun changed_or_closed_pr_cannot_be_installed_after_preview() = runTest {
        for (json in listOf(pullJson.replace(sha, "c".repeat(40)), pullJson.replace("\"open\"", "\"closed\""))) {
            val repository = DevelopmentFirmwareRepository(client { response(it, json) })
            try { repository.ensureCurrent(build, ""); fail("Expected stale PR")
            } catch (e: GitHubFirmwareException) { assertEquals(GitHubFirmwareException.Kind.STALE, e.kind) }
        }
    }

    @Test fun authenticated_redirect_never_sends_token_to_artifact_host() = runTest {
        val archive = zip(files())
        var downloads = 0
        val repository = DevelopmentFirmwareRepository(client { request ->
            if (request.url.host == "artifacts.example.com") {
                assertNull(request.header("Authorization"))
                downloads++
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(archive.toResponseBody()).build()
            } else {
                assertEquals("Bearer test-session-token", request.header("Authorization"))
                when {
                    request.url.encodedPath.endsWith("/zip") -> response(request, "", 302,
                        mapOf("Location" to "https://artifacts.example.com/build.zip"))
                    request.url.encodedPath.contains("/pulls/") -> response(request, pullJson)
                    else -> response(request, artifactJson)
                }
            }
        })
        assertArrayEquals(image, repository.download(build, "test-session-token") {}.bytes)
        assertEquals(1, downloads)
    }

    @Test fun surfaces_authentication_rate_limits_and_removed_artifacts() = runTest {
        val cases = listOf(401 to GitHubFirmwareException.Kind.AUTHENTICATION,
            403 to GitHubFirmwareException.Kind.RATE_LIMIT, 410 to GitHubFirmwareException.Kind.MISSING)
        for ((code, kind) in cases) {
            val repository = DevelopmentFirmwareRepository(client {
                response(it, "", code, if (code == 403) mapOf("X-RateLimit-Remaining" to "0") else emptyMap())
            })
            try { repository.ensureCurrent(build, ""); fail("Expected API failure")
            } catch (e: GitHubFirmwareException) { assertEquals(kind, e.kind) }
        }
    }
}
