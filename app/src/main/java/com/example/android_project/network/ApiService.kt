package com.example.android_project.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("auth")
    suspend fun auth(@Body request: AuthRequest): Response<Map<String, String>>

    @POST("mouse/move")
    suspend fun mouseMove(@Body request: MouseMoveRequest): Response<Map<String, String>>

    @POST("mouse/click")
    suspend fun mouseClick(@Body request: MouseClickRequest): Response<Map<String, String>>

    @POST("keyboard/press")
    suspend fun keyboardPress(@Body request: KeyboardPressRequest): Response<Map<String, String>>

    @POST("system/volume")
    suspend fun systemVolume(@Body request: SystemVolumeRequest): Response<Map<String, String>>

    @POST("system/launch")
    suspend fun systemLaunch(@Body request: SystemLaunchRequest): Response<Map<String, String>>

    @GET("system/status")
    suspend fun systemStatus(): Response<StatusResponse>

    @GET("screen/screenshot")
    suspend fun screenshot(): Response<ResponseBody>
}
