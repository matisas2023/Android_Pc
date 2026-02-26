package com.pcremote.client.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

data class DiscoveredServer(val host: String, val port: Int)

object ServerDiscovery {
    private const val discoveryPort = 9999
    private const val discoveryMessage = "PC_REMOTE_DISCOVERY"

    suspend fun discover(timeoutMs: Int = 2000): DiscoveredServer? = withContext(Dispatchers.IO) {
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = timeoutMs

            val data = discoveryMessage.toByteArray(Charsets.UTF_8)
            val targets = buildBroadcastTargets()
            for (target in targets) {
                socket.send(DatagramPacket(data, data.size, target, discoveryPort))
            }

            val buffer = ByteArray(2048)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val responseText = String(response.data, 0, response.length, Charsets.UTF_8)
            val payload = runCatching { JSONObject(responseText) }.getOrNull()
            val port = payload?.optInt("port", 8000) ?: 8000
            val host = response.address?.hostAddress?.takeIf { it.isNotBlank() }
                ?: payload?.optJSONArray("ips")?.optString(0)?.takeIf { it.isNotBlank() }
                ?: return@withContext null

            DiscoveredServer(host, port)
        } catch (_: Exception) {
            null
        } finally {
            socket.close()
        }
    }

    private fun buildBroadcastTargets(): List<InetAddress> {
        val targets = mutableSetOf<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }

        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
        if (interfaces != null) {
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (address in networkInterface.interfaceAddresses) {
                    address.broadcast?.let { targets.add(it) }
                }
            }
        }

        return targets.toList()
    }
}
