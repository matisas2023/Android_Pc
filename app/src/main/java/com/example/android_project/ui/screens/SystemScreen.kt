package com.example.android_project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import com.example.android_project.network.SystemMetricsResponse
import com.example.android_project.network.StatusResponse
import com.example.android_project.network.SystemLaunchRequest
import com.example.android_project.network.SystemPowerRequest
import com.example.android_project.network.SystemVolumeSetRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = remember(serverIp) { normalizeBaseUrl(serverIp) }
    val api = remember(baseUrl, token) {
        baseUrl?.let { ApiFactory.create(it, token) }
    }
    var statusMessage by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("notepad.exe") }
    var args by remember { mutableStateOf("") }
    var systemStatus by remember { mutableStateOf<StatusResponse?>(null) }
    var metrics by remember { mutableStateOf<SystemMetricsResponse?>(null) }
    var volumeLevel by remember { mutableStateOf(50f) }

    LaunchedEffect(api) {
        while (isActive) {
            val response = runCatching { api?.systemMetrics() }.getOrNull()
            if (response?.isSuccessful == true) {
                metrics = response.body()
            }
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Система") },
                actions = {
                    OutlinedButton(onClick = onBack) {
                        Text("Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard(title = "Гучність") {
                    Text(text = "Рівень: ${volumeLevel.toInt()}%")
                    Slider(
                        value = volumeLevel,
                        onValueChange = { volumeLevel = it },
                        onValueChangeFinished = {
                            scope.launch {
                                val apiService = api
                                if (apiService == null) {
                                    statusMessage = "Налаштуйте сервер"
                                    return@launch
                                }
                                statusMessage = runCatching {
                                    apiService.systemVolumeSet(SystemVolumeSetRequest(volumeLevel.toInt()))
                                }.fold(
                                    onSuccess = { response ->
                                        if (response.isSuccessful) {
                                            "Гучність встановлено"
                                        } else {
                                            "Помилка: ${response.code()}"
                                        }
                                    },
                                    onFailure = { error -> "Помилка: ${error.localizedMessage}" },
                                )
                            }
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                SectionCard(title = "Живлення") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    statusMessage = api?.let { sendPower(it, "shutdown") } ?: "Налаштуйте сервер"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Shutdown")
                        }
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    statusMessage = api?.let { sendPower(it, "restart") } ?: "Налаштуйте сервер"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Restart")
                        }
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    statusMessage = api?.let { sendPower(it, "lock") } ?: "Налаштуйте сервер"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Lock")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    statusMessage = api?.let { sendPower(it, "logoff") } ?: "Налаштуйте сервер"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Logoff")
                        }
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    statusMessage = api?.let { sendPower(it, "sleep") } ?: "Налаштуйте сервер"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Sleep")
                        }
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    statusMessage = api?.let { sendPower(it, "hibernate") } ?: "Налаштуйте сервер"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Hibernate")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Запуск програми") {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Команда запуску") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        label = { Text("Аргументи (через кому)") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                statusMessage = api?.let { sendLaunch(it, command, args) } ?: "Налаштуйте сервер"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Запустити")
                    }
                }
            }

            item {
                SectionCard(title = "Статус ПК") {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                if (api == null) {
                                    statusMessage = "Налаштуйте сервер"
                                    return@launch
                                }
                                statusMessage = "Отримання статусу..."
                                runCatching { api.systemStatus() }
                                    .onSuccess { response ->
                                        systemStatus = response.body()
                                        statusMessage = if (response.isSuccessful) {
                                            "Статус оновлено"
                                        } else {
                                            "Помилка: ${response.code()}"
                                        }
                                    }
                                    .onFailure { error ->
                                        statusMessage = "Помилка: ${error.localizedMessage}"
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Отримати статус ПК")
                    }

                    systemStatus?.let { status ->
                        Text("CPU: ${status.cpu_percent}%")
                        Text("RAM: ${status.memory.percent}% (використано ${formatBytes(status.memory.used)})")
                    }
                }
            }

            item {
                SectionCard(title = "Метрики (реальний час)") {
                    metrics?.let { data ->
                        Text("Аптайм: ${formatDuration(data.uptimeSeconds)}")
                        Text("CPU: ${formatPercent(data.cpuUsagePercent)}")
                        Text("GPU: ${formatPercent(data.gpuUsagePercent)}")
                        Text(
                            "RAM: ${formatPercent(data.memory.percent)} " +
                                "(${formatBytes(data.memory.used)} / ${formatBytes(data.memory.total)})",
                        )
                        Text(
                            "Температура CPU: ${formatTemperature(data.temperatures?.cpuCelsius)}",
                        )
                        Text(
                            "Температура GPU: ${formatTemperature(data.temperatures?.gpuCelsius)}",
                        )
                        data.battery?.let { battery ->
                            Text(
                                "Батарея: ${battery.chargePercent}% " +
                                    if (battery.isCharging) "(зарядка)" else "(живлення від батареї)",
                            )
                        } ?: Text("Батарея: немає")
                        Text(
                            "Мережа: ↓${formatSpeed(data.network.downloadBytesPerSec)} " +
                                "↑${formatSpeed(data.network.uploadBytesPerSec)}",
                        )
                        data.disks.forEach { disk ->
                            Text(
                                "Диск ${disk.name}: " +
                                    "вільно ${formatBytes(disk.freeBytes)} з ${formatBytes(disk.totalBytes)}",
                            )
                        }
                        if (data.processes.isNotEmpty()) {
                            Text("Активні процеси:")
                            data.processes.forEach { process ->
                                Text(
                                    "• ${process.name} (RAM ${formatBytes(process.memoryBytes)})",
                                )
                            }
                        }
                    } ?: Text("Немає даних (очікування)")
                }
            }

            if (statusMessage.isNotEmpty()) {
                item {
                    Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private suspend fun sendLaunch(
    api: com.example.android_project.network.ApiService,
    command: String,
    args: String,
): String {
    if (command.isBlank()) return "Вкажіть команду"
    val argsList = args.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { null }
    return runCatching {
        api.systemLaunch(SystemLaunchRequest(command = command.trim(), args = argsList))
    }.fold(
        onSuccess = { if (it.isSuccessful) "Запуск виконано" else "Помилка: ${it.code()}" },
        onFailure = { "Помилка: ${it.localizedMessage}" },
    )
}

private suspend fun sendPower(
    api: com.example.android_project.network.ApiService,
    action: String,
): String {
    return runCatching {
        api.systemPower(SystemPowerRequest(action = action))
    }.fold(
        onSuccess = { if (it.isSuccessful) "Команда виконана" else "Помилка: ${it.code()}" },
        onFailure = { "Помилка: ${it.localizedMessage}" },
    )
}

private fun formatBytes(value: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        value >= gb -> String.format("%.1f GB", value / gb)
        value >= mb -> String.format("%.1f MB", value / mb)
        value >= kb -> String.format("%.1f KB", value / kb)
        else -> "$value B"
    }
}

private fun formatPercent(value: Double?): String {
    return value?.let { String.format("%.1f%%", it) } ?: "n/a"
}

private fun formatSpeed(bytesPerSec: Double): String {
    return "${formatBytes(bytesPerSec.toLong())}/с"
}

private fun formatTemperature(value: Double?): String {
    return value?.let { String.format("%.1f°C", it) } ?: "n/a"
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, secs)
}
