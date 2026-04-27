package com.softwiredtech.dashpilot.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.softwiredtech.dashpilot.jni.VehicleBridge
import kotlinx.coroutines.Dispatchers
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
        timeoutMs: Int = 5000,
        maxRetries: Int = 3
    ): String? {
        val bridge = VehicleBridge()
        val ctx = bridge.nativeCreateContext()

        return try {
            repeat(maxRetries) {
                val result = withContext(Dispatchers.IO) {
                    bridge.nativeDiscoverPublisher(ctx, endpoint, initialIp, timeoutMs)
                }
                if (result != null) return result
            }
            null
        } finally {
            bridge.nativeDeleteContext(ctx)
        }
    }
}
