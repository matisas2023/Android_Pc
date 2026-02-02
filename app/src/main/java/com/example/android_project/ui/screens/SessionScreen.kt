package com.example.android_project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.android_project.network.SessionEndRequest
import com.example.android_project.network.SessionHeartbeatRequest
import com.example.android_project.network.SessionStartRequest
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = remember(serverIp) { normalizeBaseUrl(serverIp) }
    val api = remember(baseUrl, token) {
        baseUrl?.let { ApiFactory.create(it, token) }
    }
    var clientName by remember { mutableStateOf("android") }
    var timeoutSeconds by remember { mutableStateOf("900") }
    var sessionId by remember { mutableStateOf("") }
    var expiresAt by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Сесія користувача", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = onBack) { Text("Назад") }

        OutlinedTextField(
            value = clientName,
            onValueChange = { clientName = it },
            label = { Text("Назва клієнта") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = timeoutSeconds,
            onValueChange = { timeoutSeconds = it },
            label = { Text("Тайм-аут (сек.)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        if (api == null) {
                            statusMessage = "Налаштуйте сервер"
                            return@launch
                        }
                        val timeout = timeoutSeconds.toIntOrNull()
                        statusMessage = "Старт сесії..."
                        runCatching {
                            api.sessionStart(SessionStartRequest(clientName, timeout))
                        }.onSuccess { response ->
                            if (response.isSuccessful) {
                                val body = response.body()
                                sessionId = body?.session_id.orEmpty()
                                expiresAt = body?.expires_at.orEmpty()
                                statusMessage = "Сесію створено"
                            } else {
                                statusMessage = "Помилка: ${response.code()}"
                            }
                        }.onFailure { error ->
                            statusMessage = "Помилка: ${error.localizedMessage}"
                        }
                    }
                },
            ) {
                Text("Старт")
            }

            Button(
                onClick = {
                    scope.launch {
                        if (api == null) {
                            statusMessage = "Налаштуйте сервер"
                            return@launch
                        }
                        if (sessionId.isBlank()) {
                            statusMessage = "Немає session_id"
                            return@launch
                        }
                        statusMessage = "Оновлення..."
                        runCatching { api.sessionHeartbeat(SessionHeartbeatRequest(sessionId)) }
                            .onSuccess { response ->
                                if (response.isSuccessful) {
                                    expiresAt = response.body()?.expires_at.orEmpty()
                                    statusMessage = "Сесію продовжено"
                                } else {
                                    statusMessage = "Помилка: ${response.code()}"
                                }
                            }
                            .onFailure { error ->
                                statusMessage = "Помилка: ${error.localizedMessage}"
                            }
                    }
                },
            ) {
                Text("Heartbeat")
            }

            Button(
                onClick = {
                    scope.launch {
                        if (api == null) {
                            statusMessage = "Налаштуйте сервер"
                            return@launch
                        }
                        if (sessionId.isBlank()) {
                            statusMessage = "Немає session_id"
                            return@launch
                        }
                        statusMessage = "Завершення..."
                        runCatching { api.sessionEnd(SessionEndRequest(sessionId)) }
                            .onSuccess { response ->
                                statusMessage = if (response.isSuccessful) "Сесію завершено" else "Помилка: ${response.code()}"
                                if (response.isSuccessful) {
                                    sessionId = ""
                                    expiresAt = ""
                                }
                            }
                            .onFailure { error ->
                                statusMessage = "Помилка: ${error.localizedMessage}"
                            }
                    }
                },
            ) {
                Text("Завершити")
            }
        }

        if (sessionId.isNotBlank()) {
            Text(text = "Session ID: $sessionId")
        }
        if (expiresAt.isNotBlank()) {
            Text(text = "Дійсна до: $expiresAt")
        }

        if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
