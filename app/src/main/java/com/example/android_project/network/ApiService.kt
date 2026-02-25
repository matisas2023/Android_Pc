package com.example.android_project.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth")
    suspend fun auth(@Body request: AuthRequest): Response<ResponseBody>

    @POST("mouse/move")
    suspend fun mouseMove(@Body request: MouseMoveRequest): Response<ResponseBody>

    @POST("mouse/click")
    suspend fun mouseClick(@Body request: MouseClickRequest): Response<ResponseBody>

    @POST("keyboard/press")
    suspend fun keyboardPress(@Body request: KeyboardPressRequest): Response<ResponseBody>

    @POST("system/volume")
    suspend fun systemVolume(@Body request: SystemVolumeRequest): Response<ResponseBody>

    @POST("system/volume/set")
    suspend fun systemVolumeSet(@Body request: SystemVolumeSetRequest): Response<ResponseBody>

    @POST("system/launch")
    suspend fun systemLaunch(@Body request: SystemLaunchRequest): Response<ResponseBody>

    @GET("system/status")
    suspend fun systemStatus(): Response<StatusResponse>

    @GET("screen/screenshot")
    suspend fun screenshot(): Response<ResponseBody>

    @GET("camera/photo")
    suspend fun cameraPhoto(
        @Query("device_index") deviceIndex: Int = 0,
        @Query("quality") quality: Int = 90,
    ): Response<ResponseBody>

    @POST("session/start")
    suspend fun sessionStart(@Body request: SessionStartRequest): Response<SessionStartResponse>

    @POST("session/heartbeat")
    suspend fun sessionHeartbeat(@Body request: SessionHeartbeatRequest): Response<SessionStatusResponse>

    @POST("session/end")
    suspend fun sessionEnd(@Body request: SessionEndRequest): Response<ResponseBody>

    @POST("system/power")
    suspend fun systemPower(@Body request: SystemPowerRequest): Response<ResponseBody>

    @POST("screen/record/start")
    suspend fun screenRecordStart(@Body request: ScreenRecordStartRequest): Response<ScreenRecordStartResponse>

    @POST("screen/record/stop/{recordingId}")
    suspend fun screenRecordStop(@Path("recordingId") recordingId: String): Response<ResponseBody>

    @GET("screen/recordings")
    suspend fun screenRecordings(): Response<ScreenRecordingsResponse>

    @GET("health")
    suspend fun health(): Response<HealthResponse>

    @GET("system/metrics")
    suspend fun systemMetrics(): Response<SystemMetricsResponse>
}
