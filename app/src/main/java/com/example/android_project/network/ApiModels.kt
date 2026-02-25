package com.example.android_project.network

import com.squareup.moshi.Json

data class AuthRequest(val token: String)

data class MouseMoveRequest(
    val x: Int,
    val y: Int,
    val duration: Float = 0f,
    val absolute: Boolean = false,
)

data class MouseClickRequest(
    val button: String = "left",
    val clicks: Int = 1,
    val interval: Float = 0f,
    val x: Int? = null,
    val y: Int? = null,
)

data class KeyboardPressRequest(
    val key: String? = null,
    val keys: List<String>? = null,
    val presses: Int = 1,
    val interval: Float = 0f,
)

data class SystemVolumeRequest(
    val action: String,
    val steps: Int = 1,
)

data class SystemVolumeSetRequest(
    val level: Int,
)

data class SystemLaunchRequest(
    val command: String,
    val args: List<String>? = null,
)

data class SessionStartRequest(
    @Json(name = "clientName")
    val clientName: String? = null,
    @Json(name = "timeoutSeconds")
    val timeoutSeconds: Int? = null,
)

data class SessionHeartbeatRequest(
    @Json(name = "sessionId")
    val sessionId: String,
)

data class SessionEndRequest(
    @Json(name = "sessionId")
    val sessionId: String,
)

data class SessionStartResponse(
    @Json(name = "sessionId")
    val sessionId: String? = null,
    @Json(name = "session_id")
    val sessionIdSnake: String? = null,
    @Json(name = "expiresAt")
    val expiresAt: String? = null,
    @Json(name = "expires_at")
    val expiresAtSnake: String? = null,
)

data class SessionStatusResponse(
    @Json(name = "sessionId")
    val sessionId: String? = null,
    @Json(name = "session_id")
    val sessionIdSnake: String? = null,
    @Json(name = "expiresAt")
    val expiresAt: String? = null,
    @Json(name = "expires_at")
    val expiresAtSnake: String? = null,
)

data class SystemPowerRequest(
    val action: String,
)

data class ScreenRecordStartRequest(
    val fps: Int = 10,
    @Json(name = "durationSeconds")
    val durationSeconds: Int? = null,
)

data class ScreenRecordStartResponse(
    val status: String? = null,
    @Json(name = "recordingId")
    val recordingId: String? = null,
    @Json(name = "recording_id")
    val recordingIdSnake: String? = null,
)

data class ScreenRecordStopResponse(
    val status: String,
    @Json(name = "recordingId")
    val recordingId: String,
)

data class RecordingInfo(
    @Json(name = "startedAt")
    val startedAt: String,
    val fps: Int,
    @Json(name = "durationSeconds")
    val durationSeconds: Int?,
    val completed: Boolean,
    val file: String,
)

data class ScreenRecordingsResponse(
    val recordings: Map<String, RecordingInfo>,
)

data class StatusResponse(
    @Json(name = "cpuPercent")
    val cpuPercent: Float,
    val memory: MemoryStats,
)

data class MemoryStats(
    val total: Long,
    val available: Long,
    val used: Long,
    val percent: Float,
)

data class HealthResponse(
    val status: String,
)

data class SystemMetricsResponse(
    val uptimeSeconds: Double,
    val cpuUsagePercent: Double,
    val gpuUsagePercent: Double?,
    val memory: MetricsMemoryInfo,
    val temperatures: MetricsTemperatureInfo?,
    val battery: MetricsBatteryInfo?,
    val network: MetricsNetworkInfo,
    val disks: List<MetricsDiskInfo>,
    val processes: List<MetricsProcessInfo>,
)

data class MetricsMemoryInfo(
    val total: Long,
    val available: Long,
    val used: Long,
    val percent: Double,
)

data class MetricsTemperatureInfo(
    val cpuCelsius: Double?,
    val gpuCelsius: Double?,
)

data class MetricsBatteryInfo(
    val isPresent: Boolean,
    val chargePercent: Int,
    val isCharging: Boolean,
    val secondsRemaining: Int?,
)

data class MetricsNetworkInfo(
    val downloadBytesPerSec: Double,
    val uploadBytesPerSec: Double,
)

data class MetricsDiskInfo(
    val name: String,
    val totalBytes: Long,
    val freeBytes: Long,
)

data class MetricsProcessInfo(
    val id: Int,
    val name: String,
    val memoryBytes: Long,
    val cpuSeconds: Double,
)
