package com.example.android_project.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

data class DiscoveredServer(
    val host: String,
    val port: Int,
    val token: String?,
    val tunnelUrl: String?,
    val externalUrl: String?,
)

object ServerDiscovery {
    private const val discoveryPort = 9999
    private const val discoveryMessage = "PC_REMOTE_DISCOVERY"

    suspend fun discover(timeoutMs: Int = 2000): DiscoveredServer? = withContext(Dispatchers.IO) {
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = timeoutMs

            val data = discoveryMessage.toByteArray(Charsets.UTF_8)
            val broadcastTargets = buildBroadcastTargets()
            for (target in broadcastTargets) {
                val packet = DatagramPacket(
                    data,
                    data.size,
                    target,
                    discoveryPort,
                )
                socket.send(packet)
            }

            val buffer = ByteArray(1024)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val responseText = String(response.data, 0, response.length, Charsets.UTF_8)
            val payload = runCatching { JSONObject(responseText) }.getOrNull()
            val port = payload?.optInt("port", 8000) ?: 8000
            val token = payload?.optString("token", null)
            val tunnelUrl = payload?.optString("tunnelUrl", null)?.takeIf { it.isNotBlank() }
            val externalUrl = payload?.optString("externalUrl", null)?.takeIf { it.isNotBlank() }
            val payloadHost = payload?.optJSONArray("ips")
                ?.optString(0)
                ?.takeIf { it.isNotBlank() }
            val responseAddress = response.address
            val host = when {
                responseAddress == null -> payloadHost
                responseAddress.isAnyLocalAddress || responseAddress.isLoopbackAddress -> payloadHost
                else -> responseAddress.hostAddress
            } ?: responseAddress?.hostAddress ?: payloadHost ?: return@withContext null
            DiscoveredServer(
                host = host,
                port = port,
                token = token,
                tunnelUrl = tunnelUrl,
                externalUrl = externalUrl,
            )
        } catch (error: Exception) {
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
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }
                for (address in networkInterface.interfaceAddresses) {
                    val broadcast = address.broadcast
                    if (broadcast != null) {
                        targets.add(broadcast)
                    }
                }
            }
        }

        return targets.toList()
    }
}
