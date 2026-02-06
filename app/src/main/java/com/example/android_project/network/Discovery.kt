package com.example.android_project.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

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
            val packet = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName("255.255.255.255"),
                discoveryPort,
            )
            socket.send(packet)

            val buffer = ByteArray(1024)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val responseText = String(response.data, 0, response.length, Charsets.UTF_8)
            val payload = runCatching { JSONObject(responseText) }.getOrNull()
            val port = payload?.optInt("port", 8000) ?: 8000
            val token = payload?.optString("token", null)
            val tunnelUrl = payload?.optString("tunnelUrl", null)?.takeIf { it.isNotBlank() }
            val externalUrl = payload?.optString("externalUrl", null)?.takeIf { it.isNotBlank() }
            DiscoveredServer(
                host = response.address.hostAddress,
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
}
