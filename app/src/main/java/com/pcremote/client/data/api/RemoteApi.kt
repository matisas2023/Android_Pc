package com.pcremote.client.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import com.pcremote.client.domain.model.PairingCodeResponse
import com.pcremote.client.domain.model.PairingTokenResponse
import com.pcremote.client.domain.model.PowerRequest
import com.pcremote.client.domain.model.StatusResponse

data class PairingRequest(val code: String, val clientName: String)

interface RemoteApi {
    @GET("api/v1/pairing/code")
    suspend fun pairingCode(): Response<PairingCodeResponse>

    @POST("api/v1/pairing/pair")
    suspend fun pair(@Body request: PairingRequest): Response<PairingTokenResponse>

    @GET("api/v1/status")
    suspend fun status(@Header("Authorization") bearer: String): Response<StatusResponse>

    @GET("api/v1/screen/screenshot")
    suspend fun screenshot(@Header("Authorization") bearer: String): Response<ResponseBody>

    @GET("api/v1/camera/photo")
    suspend fun cameraPhoto(@Header("Authorization") bearer: String): Response<ResponseBody>

    @POST("api/v1/system/power")
    suspend fun power(
        @Header("Authorization") bearer: String,
        @Header("X-Timestamp") timestamp: String,
        @Header("X-Nonce") nonce: String,
        @Body request: PowerRequest,
    ): Response<ResponseBody>
}
