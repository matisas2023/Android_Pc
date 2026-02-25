package com.pcremote.client.data.repository

import com.pcremote.client.data.api.ApiFactory
import com.pcremote.client.data.api.PairingRequest
import com.pcremote.client.domain.model.PowerRequest
import com.pcremote.client.domain.model.StatusResponse
import java.util.UUID

class RemoteRepository {
    suspend fun pair(baseUrl: String, code: String, clientName: String): Result<String> = runCatching {
        val api = ApiFactory.create(baseUrl)
        val response = api.pair(PairingRequest(code, clientName))
        if (!response.isSuccessful) error("Pair failed: ${response.code()}")
        response.body()?.token ?: error("Token missing")
    }

    suspend fun status(baseUrl: String, token: String): Result<StatusResponse> = runCatching {
        val api = ApiFactory.create(baseUrl)
        val response = api.status("Bearer $token")
        if (!response.isSuccessful) error("Status failed: ${response.code()}")
        response.body() ?: error("Empty status")
    }

    suspend fun screenshot(baseUrl: String, token: String): Result<ByteArray> = runCatching {
        val api = ApiFactory.create(baseUrl)
        val response = api.screenshot("Bearer $token")
        if (!response.isSuccessful) error("Screenshot failed: ${response.code()}")
        response.body()?.bytes() ?: error("Screenshot body empty")
    }

    suspend fun cameraPhoto(baseUrl: String, token: String): Result<ByteArray> = runCatching {
        val api = ApiFactory.create(baseUrl)
        val response = api.cameraPhoto("Bearer $token")
        if (!response.isSuccessful) error("Camera photo failed: ${response.code()}")
        response.body()?.bytes() ?: error("Camera body empty")
    }

    suspend fun power(baseUrl: String, token: String, action: String): Result<Unit> = runCatching {
        val api = ApiFactory.create(baseUrl)
        val ts = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val response = api.power("Bearer $token", ts, nonce, PowerRequest(action))
        if (!response.isSuccessful) error("Power failed: ${response.code()}")
    }
}
