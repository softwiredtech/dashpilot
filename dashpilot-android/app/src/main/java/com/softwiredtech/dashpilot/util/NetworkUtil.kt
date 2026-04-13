package com.softwiredtech.dashpilot.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.softwiredtech.dashpilot.jni.VehicleBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkUtil(private val context: Context) {
    fun getWifiIpAddress(): String? {
        return getIpFromConnectivityManager() ?: getHotspotIpAddress()
    }

    private fun getIpFromConnectivityManager(): String? {
        val connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null

        return linkProperties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress }
            ?.hostAddress
    }

    private fun getHotspotIpAddress(): String? {
        val hotspotPrefixes = listOf("ap", "swlan", "wlan")
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { iface ->
                    iface.isUp && hotspotPrefixes.any { iface.name.startsWith(it) }
                }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    suspend fun findCanPublisher(
        initialIp: String,
        endpoint: String = "can",
        timeoutMs: Int = 400,
        parallelism: Int = 16
    ): String? {
        val prefix = initialIp.substringBeforeLast('.', "")
        if (prefix.isEmpty()) return null
        val initialLastOctet = initialIp.substringAfterLast('.').toIntOrNull() ?: return null

        val bridge = VehicleBridge()
        val ctx = bridge.nativeCreateContext()

        return try {
            withContext(Dispatchers.IO) {
                // Try the initial address first; if it hits, skip the subnet scan entirely.
                if (bridge.nativeProbeSubSocket(ctx, endpoint, initialIp, timeoutMs)) {
                    return@withContext initialIp
                }

                val semaphore = Semaphore(parallelism)
                val firstHit = CompletableDeferred<String?>()

                coroutineScope {
                    val jobs = (1..254)
                        .filter { it != initialLastOctet }
                        .map { octet ->
                            async {
                                if (firstHit.isCompleted) return@async
                                semaphore.withPermit {
                                    if (firstHit.isCompleted) return@withPermit
                                    val candidate = "$prefix.$octet"
                                    val found = bridge.nativeProbeSubSocket(
                                        ctx, endpoint, candidate, timeoutMs
                                    )
                                    if (found) firstHit.complete(candidate)
                                }
                            }
                        }
                    // Wait for either a hit or all probes to finish.
                    jobs.awaitAll()
                    if (!firstHit.isCompleted) firstHit.complete(null)
                }

                firstHit.await()
            }
        } finally {
            bridge.nativeDeleteContext(ctx)
        }
    }
}
