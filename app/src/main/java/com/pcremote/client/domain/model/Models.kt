package com.pcremote.client.domain.model

data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val token: String,
)

data class PairingCodeResponse(
    val value: String,
    val expiresAtUtc: String,
)

data class PairingTokenResponse(
    val token: String,
    val expiresAtUtc: String,
)

data class StatusResponse(
    val uptimeSeconds: Double,
    val machineName: String,
    val userName: String,
    val ips: List<String>,
)

data class PowerRequest(val action: String)
