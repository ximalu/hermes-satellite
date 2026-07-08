package com.hermes.satellite.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
    suspend fun scanArpTable(): List<Device> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<Device>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
                reader.forEachLine { line ->
                    try {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                            devices.add(Device(
                                ip = parts[0],
                                mac = parts[3],
                                interfaceName = if (parts.size > 5) parts[5] else ""
                            ))
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        devices
    }

    /** Ping scan local subnet — concurrent, ~3s for /24 */
    suspend fun pingScan(): List<Device> = withContext(Dispatchers.IO) {
        try {
            val localIp = getLocalIp() ?: return@withContext emptyList()
            val prefix = localIp.substringBeforeLast('.')
            if (prefix.isEmpty()) return@withContext emptyList()

            val devices = Collections.synchronizedList(mutableListOf<Device>())

            coroutineScope {
                val tasks = (1..254).map { lastOctet ->
                    async {
                        try {
                            val ip = "$prefix.$lastOctet"
                            val reachable = withTimeoutOrNull(200) {
                                val addr = InetAddress.getByName(ip)
                                if (addr.isReachable(200)) addr else null
                            }
                            if (reachable != null) {
                                val hostname = withTimeoutOrNull(500) {
                                    reachable.getCanonicalHostName()
                                } ?: ""
                                val displayHostname = if (hostname == ip) "" else hostname
                                devices.add(Device(ip = ip, hostname = displayHostname))
                            }
                        } catch (_: Exception) { }
                    }
                }
                tasks.awaitAll()
            }

            devices.toList()
        } catch (_: Exception) {
            emptyList()
        }
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
                        if (ip.startsWith("0.") || ip.startsWith("169.254.")) continue
                        return ip
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /** Merge ARP + Ping results, dedup by IP */
    fun mergeResults(arpDevices: List<Device>, pingDevices: List<Device>): List<Device> {
        val merged = LinkedHashMap<String, Device>()
        arpDevices.forEach { merged[it.ip] = it }
        pingDevices.forEach { ping ->
            merged.merge(ping.ip, ping) { old, new ->
                old.copy(hostname = if (old.hostname.isEmpty()) new.hostname else old.hostname)
            }
        }
        return merged.values.toList().sortedBy { it.ip }
    }
}
