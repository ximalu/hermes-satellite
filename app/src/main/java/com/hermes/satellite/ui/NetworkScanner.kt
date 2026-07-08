package com.hermes.satellite.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

object NetworkScanner {

    data class Device(
        val ip: String,
        val hostname: String = "",
        val mac: String = "",
        val interfaceName: String = ""
    )

    /** Read ARP table — instant, shows recently active devices */
    suspend fun scanArpTable(): List<Device> = with(Dispatchers.IO) {
        val devices = mutableListOf<Device>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                reader.forEachLine { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                        devices.add(Device(
                            ip = parts[0],
                            mac = parts[3],
                            interfaceName = if (parts.size > 5) parts[5] else ""
                        ))
                    }
                }
            }
        } catch (_: Exception) { }
        devices
    }

    /** Ping scan local subnet — concurrent, ~3s for /24 */
    suspend fun pingScan(): List<Device> = with(Dispatchers.IO) {
        val localIp = getLocalIp() ?: return@with emptyList()
        val prefix = localIp.substringBeforeLast('.')
        if (prefix.isEmpty()) return@with emptyList()

        val devices = Collections.synchronizedList(mutableListOf<Device>())

        coroutineScope {
            val tasks = (1..254).map { lastOctet ->
                async {
                    val ip = "$prefix.$lastOctet"
                    val reachable = withTimeoutOrNull(200) {
                        val addr = InetAddress.getByName(ip)
                        if (addr.isReachable(200)) addr else null
                    }
                    if (reachable != null) {
                        // Try reverse DNS (short timeout, best-effort)
                        val hostname = withTimeoutOrNull(1000) {
                            reachable.getCanonicalHostName()
                        } ?: ""
                        val displayHostname = if (hostname == ip) "" else hostname
                        devices.add(Device(ip = ip, hostname = displayHostname))
                    }
                }
            }
            tasks.awaitAll()
        }

        devices.toList()
    }

    /** Get local IP on the WiFi/eth interface */
    private fun getLocalIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                val addr = ni.inetAddresses ?: continue
                while (addr.hasMoreElements()) {
                    val inet = addr.nextElement()
                    if (!inet.isLoopbackAddress && inet is java.net.Inet4Address) {
                        val ip = inet.hostAddress ?: continue
                        // Skip 0.0.0.0 and link-local
                        if (ip.startsWith("0.") || ip.startsWith("169.254.")) continue
                        return ip
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /** Merge ARP + Ping results, dedup by IP. ARP has MAC, Ping has reachability */
    fun mergeResults(arpDevices: List<Device>, pingDevices: List<Device>): List<Device> {
        val merged = LinkedHashMap<String, Device>()
        // ARP devices first (has MAC info)
        arpDevices.forEach { merged[it.ip] = it }
        // Ping devices fill in hostname if missing
        pingDevices.forEach { ping ->
            merged.merge(ping.ip, ping) { old, new ->
                old.copy(hostname = if (old.hostname.isEmpty()) new.hostname else old.hostname)
            }
        }
        return merged.values.toList().sortedBy { it.ip }
    }
}
