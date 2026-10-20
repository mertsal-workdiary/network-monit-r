package com.example.networkmonitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class ProbeResult(
    val success: Boolean,
    val latencyMs: Long?,
    val error: String?
)

object NetworkProbe {
    fun gatewayAddress(context: Context): String? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return null
        val properties = manager.getLinkProperties(network) ?: return null
        return properties.routes
            .asSequence()
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway }
            .firstOrNull { it is Inet4Address }
            ?.hostAddress
            ?: properties.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
    }

    fun networkType(context: Context): String {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "Bağlantı yok"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Diğer"
        }
    }

    fun run(host: String, publicTarget: Boolean): ProbeResult {
        val start = System.nanoTime()
        var pingError: String? = null

        try {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", host)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                pingError = "Ping zaman aşımı"
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.exitValue() == 0) {
                    val parsed = Regex("time[=<]([0-9.]+)\\s*ms").find(output)
                        ?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toLong()
                    return ProbeResult(true, parsed ?: elapsedMs(start), null)
                }
                pingError = "Ping yanıtı yok"
            }
        } catch (exception: Exception) {
            pingError = exception.message ?: exception.javaClass.simpleName
        }

        try {
            val fallbackStart = System.nanoTime()
            if (publicTarget) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, 443), 2500)
                }
            } else {
                val reachable = InetAddress.getByName(host).isReachable(2500)
                if (!reachable) return ProbeResult(false, null, pingError ?: "Hedefe ulaşılamadı")
            }
            return ProbeResult(true, elapsedMs(fallbackStart), null)
        } catch (exception: Exception) {
            val detail = exception.message ?: exception.javaClass.simpleName
            return ProbeResult(false, null, "$pingError; alternatif kontrol: $detail")
        }
    }

    private fun elapsedMs(start: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
}
