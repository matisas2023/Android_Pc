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

data class SystemLaunchRequest(
    val command: String,
    val args: List<String>? = null,
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
