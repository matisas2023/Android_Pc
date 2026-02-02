package com.example.android_project.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

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

    @GET("camera/photo")
    suspend fun cameraPhoto(): Response<ResponseBody>

    @POST("session/start")
    suspend fun sessionStart(@Body request: SessionStartRequest): Response<SessionStartResponse>

    @POST("session/heartbeat")
    suspend fun sessionHeartbeat(@Body request: SessionHeartbeatRequest): Response<SessionStatusResponse>

    @POST("session/end")
    suspend fun sessionEnd(@Body request: SessionEndRequest): Response<Map<String, String>>

    @POST("system/power")
    suspend fun systemPower(@Body request: SystemPowerRequest): Response<Map<String, String>>

    @POST("screen/record/start")
    suspend fun screenRecordStart(@Body request: ScreenRecordStartRequest): Response<ScreenRecordStartResponse>

    @POST("screen/record/stop/{recordingId}")
    suspend fun screenRecordStop(@Path("recordingId") recordingId: String): Response<ScreenRecordStopResponse>

    @GET("screen/recordings")
    suspend fun screenRecordings(): Response<ScreenRecordingsResponse>

    @GET("health")
    suspend fun health(): Response<HealthResponse>
}
