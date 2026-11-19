package com.softwiredtech.dashpilot.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class FirmwarePullRequest(val number: Int, val title: String, val commit: String)
data class FirmwarePrRun(
    val id: Long, val commit: String, val status: String, val conclusion: String?,
    val createdAt: Instant, val attempt: Int, val pulls: List<Int>,
    val event: String = "pull_request", val path: String = ".github/workflows/pr-firmware.yml",
) {
    val url get() = "https://github.com/softwiredtech/dashkit-firmware/actions/runs/$id"
}
data class FirmwarePrArtifact(
    val id: Long, val name: String, val expired: Boolean, val expiresAt: Instant,
    val createdAt: Instant,
)
data class FirmwarePrBuild(val pull: FirmwarePullRequest, val run: FirmwarePrRun, val artifact: FirmwarePrArtifact)
data class VerifiedPrFirmware(val build: FirmwarePrBuild, val version: String, val bytes: ByteArray)

enum class PrBuildProblem { PENDING, FAILED, MISSING, EXPIRED }
sealed interface PrBuildSelection {
    data class Ready(val build: FirmwarePrBuild) : PrBuildSelection
    data class Unavailable(val problem: PrBuildProblem, val run: FirmwarePrRun? = null) : PrBuildSelection
}

class GitHubFirmwareException(val kind: Kind, val retryAt: String? = null) : Exception(kind.name) {
    enum class Kind { AUTHENTICATION, RATE_LIMIT, MISSING, WORKFLOW_MISSING, NETWORK, INVALID, STALE }
}

/** Public metadata; a user-supplied, session-only credential is used only on api.github.com. */
class DevelopmentFirmwareRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(false).build(),
) {

    suspend fun pulls(token: String): List<FirmwarePullRequest> = withContext(Dispatchers.IO) {
        pages("/pulls?state=open&sort=updated&direction=desc", null, token).map { parsePull(it) }
    }

    suspend fun findBuild(pull: FirmwarePullRequest, token: String): PrBuildSelection = withContext(Dispatchers.IO) {
        val current = currentPull(pull, token)
        val runs = try {
            pages("/actions/workflows/pr-firmware.yml/runs?event=pull_request&head_sha=${current.commit}",
                "workflow_runs", token).map(::parseRun)
        } catch (e: GitHubFirmwareException) {
            if (e.kind == GitHubFirmwareException.Kind.MISSING) {
                throw GitHubFirmwareException(GitHubFirmwareException.Kind.WORKFLOW_MISSING)
            }
            throw e
        }
        val run = latestSuccessfulRun(current, runs)
        if (run == null) {
            val latest = matchingRuns(current, runs).firstOrNull()
            return@withContext PrBuildSelection.Unavailable(
                if (latest == null || latest.status != "completed") PrBuildProblem.PENDING else PrBuildProblem.FAILED,
                latest,
            )
        }
        val artifacts = pages("/actions/runs/${run.id}/artifacts?", "artifacts", token).map(::parseArtifact)
        selectArtifact(current, run, artifacts, Instant.now())
    }

    suspend fun download(build: FirmwarePrBuild, token: String, onProgress: (Float) -> Unit): VerifiedPrFirmware =
        withContext(Dispatchers.IO) {
            ensureCurrent(build, token)
            val request = request("/actions/artifacts/${build.artifact.id}/zip", token)
            val location = client.newCall(request).execute().use { response ->
                checkResponse(response, download = true)
                if (response.code != 302) throw GitHubFirmwareException(GitHubFirmwareException.Kind.INVALID)
                response.header("Location") ?: throw GitHubFirmwareException(GitHubFirmwareException.Kind.INVALID)
            }
            // The signed download URL is short-lived. Never forward the GitHub token to it.
            val url = location.toHttpUrlOrNull()
                ?: throw GitHubFirmwareException(GitHubFirmwareException.Kind.INVALID)
            if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) invalid()
            val archive = client.newBuilder().followRedirects(true).followSslRedirects(false).build()
                .newCall(Request.Builder().url(url).build()).execute().use { response ->
                    checkResponse(response)
                    val body = response.body ?: invalid()
                    val total = body.contentLength()
                    if (total > MAX_ARCHIVE_BYTES) invalid()
                    val context = currentCoroutineContext()
                    readLimited(body.byteStream(), MAX_ARCHIVE_BYTES) { count ->
                        context.ensureActive()
                        if (total > 0) onProgress((count.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            currentCoroutineContext().ensureActive()
            val verified = verifyArchive(archive, build)
            ensureCurrent(build, token)
            verified
        }

    suspend fun ensureCurrent(build: FirmwarePrBuild, token: String) = withContext(Dispatchers.IO) {
        currentPull(build.pull, token)
        val artifact = get("/actions/artifacts/${build.artifact.id}", token)
        val current = parseArtifact(artifact)
        if (current.name != build.artifact.name || current.expired || !current.expiresAt.isAfter(Instant.now())) {
            throw GitHubFirmwareException(GitHubFirmwareException.Kind.MISSING)
        }
    }

    private fun currentPull(expected: FirmwarePullRequest, token: String): FirmwarePullRequest {
        val json = get("/pulls/${expected.number}", token)
        val pull = parsePull(json)
        if (json["state"].asString != "open" || pull.commit != expected.commit) {
            throw GitHubFirmwareException(GitHubFirmwareException.Kind.STALE)
        }
        return pull
    }

    private fun request(path: String, token: String): Request = Request.Builder().url(API + path)
        .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "DashPilot").apply {
            if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}")
        }.build()

    private fun get(path: String, token: String): JsonObject = client.newCall(request(path, token)).execute().use {
        checkResponse(it)
        JsonParser.parseString(it.body?.string() ?: invalid()).asJsonObject
    }

    private fun pages(path: String, key: String?, token: String): List<JsonObject> {
        val entries = mutableListOf<JsonObject>()
        for (page in 1..100) {
            val separator = if ('?' in path) "&" else "?"
            val more = client.newCall(request("$path${separator}per_page=100&page=$page", token)).execute().use {
                checkResponse(it)
                val json = JsonParser.parseString(it.body?.string() ?: invalid())
                val array = if (key == null) json.asJsonArray else json.asJsonObject.getAsJsonArray(key)
                entries += array.map { item -> item.asJsonObject }
                it.header("Link").orEmpty().contains("rel=\"next\"")
            }
            if (!more) return entries
        }
        invalid() // Never silently truncate a result set.
    }

    private fun checkResponse(response: Response, download: Boolean = false) {
        val kind = when {
            response.code == 429 || (response.code == 403 &&
                (response.header("X-RateLimit-Remaining") == "0" || response.header("Retry-After") != null ||
                    response.peekBody(4096).string().contains("rate limit", ignoreCase = true))) ->
                GitHubFirmwareException.Kind.RATE_LIMIT
            response.code == 401 || response.code == 403 -> GitHubFirmwareException.Kind.AUTHENTICATION
            response.code == 404 || response.code == 410 -> GitHubFirmwareException.Kind.MISSING
            response.isSuccessful || (download && response.code == 302) -> return
            else -> GitHubFirmwareException.Kind.NETWORK
        }
        val retry = response.header("Retry-After")?.toLongOrNull()?.let { Instant.now().plusSeconds(it).toString() }
            ?: response.header("X-RateLimit-Reset")?.toLongOrNull()?.let { Instant.ofEpochSecond(it).toString() }
        throw GitHubFirmwareException(kind, retry)
    }

    companion object {
        private const val API = "https://api.github.com/repos/softwiredtech/dashkit-firmware"
        const val WORKFLOW_URL = "https://github.com/softwiredtech/dashkit-firmware/actions/workflows/pr-firmware.yml"
        private const val OTA = "dashkit-firmware.bin"
        private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

        internal fun parsePull(json: JsonObject) = FirmwarePullRequest(json["number"].asInt,
            json["title"].asString, json.getAsJsonObject("head")["sha"].asString.also {
                if (!it.matches(Regex("[a-f0-9]{40}"))) invalid()
            })

        internal fun parseRun(json: JsonObject) = FirmwarePrRun(json["id"].asLong, json["head_sha"].asString,
            json["status"].asString, json["conclusion"]?.takeUnless { it.isJsonNull }?.asString,
            Instant.parse(json["created_at"].asString), json["run_attempt"]?.asInt ?: 1,
            json.getAsJsonArray("pull_requests")?.map { it.asJsonObject["number"].asInt }.orEmpty(),
            json["event"].asString, json["path"].asString)

        internal fun parseArtifact(json: JsonObject) = FirmwarePrArtifact(json["id"].asLong,
            json["name"].asString, json["expired"].asBoolean, Instant.parse(json["expires_at"].asString),
            Instant.parse(json["created_at"].asString))

        private fun matchingRuns(pull: FirmwarePullRequest, runs: List<FirmwarePrRun>) = runs.filter {
            it.commit == pull.commit && it.event == "pull_request" &&
                it.path.substringBefore('@') == ".github/workflows/pr-firmware.yml" &&
                (it.pulls.isEmpty() || pull.number in it.pulls)
        }.sortedWith(compareByDescending<FirmwarePrRun> { it.createdAt }
            .thenByDescending { it.id }.thenByDescending { it.attempt })

        internal fun latestSuccessfulRun(pull: FirmwarePullRequest, runs: List<FirmwarePrRun>) =
            matchingRuns(pull, runs).firstOrNull { it.status == "completed" && it.conclusion == "success" }

        internal fun selectArtifact(pull: FirmwarePullRequest, run: FirmwarePrRun,
            artifacts: List<FirmwarePrArtifact>, now: Instant): PrBuildSelection {
            val prefix = "dashkit-pr-${pull.number}-"
            val matching = artifacts.filter {
                val short = it.name.removePrefix(prefix)
                it.name.startsWith(prefix) && short.length in 7..40 && pull.commit.startsWith(short)
            }.sortedByDescending { it.createdAt }
            val artifact = matching.firstOrNull { !it.expired && it.expiresAt.isAfter(now) }
            return if (artifact != null) PrBuildSelection.Ready(FirmwarePrBuild(pull, run, artifact))
            else PrBuildSelection.Unavailable(if (matching.isEmpty()) PrBuildProblem.MISSING else PrBuildProblem.EXPIRED, run)
        }

        /** Extract only root OTA files into bounded memory; never write archive paths to disk. */
        internal fun verifyArchive(zip: ByteArray, build: FirmwarePrBuild): VerifiedPrFirmware = try {
            verifyContents(zip, build)
        } catch (e: Exception) {
            invalid()
        }

        private fun verifyContents(zip: ByteArray, build: FirmwarePrBuild): VerifiedPrFirmware {
            val files = mutableMapOf<String, ByteArray>()
            var total = 0
            var count = 0
            ZipInputStream(zip.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (++count > 32 || entry.name.contains('\\') || entry.name.startsWith('/') ||
                        entry.name.split('/').any { it == ".." || it == "." }) invalid()
                    if (entry.isDirectory) continue
                    val limit = if (entry.name.endsWith(".bin")) MAX_IMAGE_BYTES else 64 * 1024
                    val bytes = readLimited(input, limit) { }
                    total += bytes.size
                    if (total > MAX_ARCHIVE_BYTES || files.put(entry.name, bytes) != null) invalid()
                }
            }
            val image = files[OTA] ?: invalid()
            if (image.firstOrNull() != 0xE9.toByte()) invalid()
            val checksums = files["SHA256SUMS.txt"]?.toString(Charsets.UTF_8) ?: invalid()
            val hashes = checksums.lineSequence().mapNotNull { line ->
                val match = Regex("^([a-fA-F0-9]{64})[ \\t]+\\*?(?:\\./)?dashkit-firmware\\.bin$").matchEntire(line.trim())
                match?.groupValues?.get(1)?.lowercase()
            }.toList()
            val actual = MessageDigest.getInstance("SHA-256").digest(image).joinToString("") { "%02x".format(it) }
            if (hashes.size != 1 || hashes.single() != actual) invalid()
            val info = JsonParser.parseString(files["build-info.json"]?.toString(Charsets.UTF_8) ?: invalid()).asJsonObject
            val version = info["version"]?.asString ?: invalid()
            val suffix = version.removePrefix("pr-${build.pull.number}-")
            if (info["pull_request"]?.asInt != build.pull.number || info["commit"]?.asString != build.pull.commit ||
                info["target"]?.asString != "esp32s3" || info["run_url"]?.asString != build.run.url ||
                version != "pr-${build.pull.number}-$suffix" || suffix.length !in 7..40 ||
                !build.pull.commit.startsWith(suffix) || build.run.commit != build.pull.commit) invalid()
            return VerifiedPrFirmware(build, version, image)
        }

        private fun readLimited(input: InputStream, limit: Int, onRead: (Int) -> Unit): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) return out.toByteArray()
                if (out.size() + read > limit) invalid()
                out.write(buffer, 0, read)
                onRead(out.size())
            }
        }

        private fun invalid(): Nothing = throw GitHubFirmwareException(GitHubFirmwareException.Kind.INVALID)
    }
}
