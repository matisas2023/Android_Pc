package com.example.android_project.network

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
    val client_name: String? = null,
    val timeout_seconds: Int? = null,
)

data class SessionHeartbeatRequest(
    val session_id: String,
)

data class SessionEndRequest(
    val session_id: String,
)

data class SessionStartResponse(
    val session_id: String,
    val expires_at: String,
)

data class SessionStatusResponse(
    val session_id: String,
    val expires_at: String,
)

data class SystemPowerRequest(
    val action: String,
)

data class ScreenRecordStartRequest(
    val fps: Int = 10,
    val duration_seconds: Int? = null,
)

data class ScreenRecordStartResponse(
    val status: String,
    val recording_id: String,
)

data class ScreenRecordStopResponse(
    val status: String,
    val recording_id: String,
)

data class RecordingInfo(
    val started_at: String,
    val fps: Int,
    val duration_seconds: Int?,
    val completed: Boolean,
    val file: String,
)

data class ScreenRecordingsResponse(
    val recordings: Map<String, RecordingInfo>,
)

data class StatusResponse(
    val cpu_percent: Float,
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
