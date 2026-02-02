package com.example.android_project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сесія користувача") },
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
                SectionCard(title = "Параметри") {
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
                }
            }

            item {
                SectionCard(title = "Керування") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilledTonalButton(
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Старт")
                        }

                        FilledTonalButton(
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Heartbeat")
                        }

                        FilledTonalButton(
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
                                            statusMessage = if (response.isSuccessful) {
                                                "Сесію завершено"
                                            } else {
                                                "Помилка: ${response.code()}"
                                            }
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Завершити")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Стан") {
                    if (sessionId.isNotBlank()) {
                        Text(text = "Session ID: $sessionId")
                    }
                    if (expiresAt.isNotBlank()) {
                        Text(text = "Дійсна до: $expiresAt")
                    }
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
    content: @Composable Column.() -> Unit,
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
