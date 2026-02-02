package com.example.android_project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.android_project.network.KeyboardPressRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KeyboardScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = remember(serverIp) { normalizeBaseUrl(serverIp) }
    val api = remember(baseUrl, token) {
        baseUrl?.let { ApiFactory.create(it, token) }
    }
    var inputText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Керування клавіатурою", style = MaterialTheme.typography.headlineMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onBack) { Text("Назад") }
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendKey(it, "enter") } ?: "Налаштуйте сервер"
                    }
                },
            ) {
                Text("Enter")
            }
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendKey(it, "backspace") } ?: "Налаштуйте сервер"
                    }
                },
            ) {
                Text("Backspace")
            }
        }

        Text(text = "Стрілки керування", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendKey(it, "up") } ?: "Налаштуйте сервер"
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("↑")
            }
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendKey(it, "down") } ?: "Налаштуйте сервер"
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("↓")
            }
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendKey(it, "left") } ?: "Налаштуйте сервер"
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("←")
            }
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendKey(it, "right") } ?: "Налаштуйте сервер"
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("→")
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { newText ->
                val previousText = inputText
                inputText = newText
                if (api == null) {
                    status = "Налаштуйте сервер"
                } else {
                    scope.launch {
                        status = sendTextDelta(api, previousText, newText)
                    }
                }
            },
            label = { Text("Друкуйте текст") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (status.isNotEmpty()) {
            Text(text = status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun sendKey(api: com.example.android_project.network.ApiService, key: String): String {
    return runCatching {
        api.keyboardPress(KeyboardPressRequest(key = key))
    }.fold(
        onSuccess = { if (it.isSuccessful) "Команда виконана" else "Помилка: ${it.code()}" },
        onFailure = { "Помилка: ${it.localizedMessage}" },
    )
}

private suspend fun sendText(api: com.example.android_project.network.ApiService, text: String): String {
    if (text.isBlank()) return "Введіть текст"
    return runCatching {
        for (char in text) {
            val key = when (char) {
                ' ' -> "space"
                '\n' -> "enter"
                else -> char.toString()
            }
            api.keyboardPress(KeyboardPressRequest(key = key))
            delay(30)
        }
    }.fold(
        onSuccess = { "Текст надіслано" },
        onFailure = { "Помилка: ${it.localizedMessage}" },
    )
}

private suspend fun sendTextDelta(
    api: com.example.android_project.network.ApiService,
    previousText: String,
    newText: String,
): String {
    if (previousText == newText) return "Готово"
    return when {
        newText.startsWith(previousText) -> {
            sendText(api, newText.removePrefix(previousText))
        }
        previousText.startsWith(newText) -> {
            val backspaces = previousText.length - newText.length
            runCatching {
                repeat(backspaces) {
                    api.keyboardPress(KeyboardPressRequest(key = "backspace"))
                    delay(20)
                }
            }.fold(
                onSuccess = { "Готово" },
                onFailure = { "Помилка: ${it.localizedMessage}" },
            )
        }
        else -> {
            runCatching {
                repeat(previousText.length) {
                    api.keyboardPress(KeyboardPressRequest(key = "backspace"))
                    delay(20)
                }
            }.fold(
                onSuccess = { sendText(api, newText) },
                onFailure = { "Помилка: ${it.localizedMessage}" },
            )
        }
    }
}
