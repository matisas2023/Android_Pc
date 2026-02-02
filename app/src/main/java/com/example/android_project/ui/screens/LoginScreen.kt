package com.example.android_project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import com.example.android_project.network.AuthRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    settingsRepository: SettingsRepository,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var serverIp by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        serverIp = settingsRepository.serverIpFlow.first()
        token = settingsRepository.tokenFlow.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Підключення", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = serverIp,
            onValueChange = { serverIp = it },
            label = { Text("IP або URL сервера (наприклад, 192.168.0.10:8000)") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("API token") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                scope.launch {
                    settingsRepository.saveSettings(serverIp.trim(), token.trim())
                    onContinue()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Зберегти і продовжити")
        }

        Button(
            onClick = {
                scope.launch {
                    statusMessage = "Перевірка..."
                    val baseUrl = normalizeBaseUrl(serverIp)
                    if (baseUrl == null || token.isBlank()) {
                        statusMessage = "Заповніть IP та token"
                        return@launch
                    }
                    runCatching {
                        val api = ApiFactory.create(baseUrl, token)
                        api.auth(AuthRequest(token))
                    }.onSuccess { response ->
                        statusMessage = if (response.isSuccessful) {
                            "Підключення успішне"
                        } else {
                            "Помилка: ${response.code()}"
                        }
                    }.onFailure { error ->
                        statusMessage = "Помилка: ${error.localizedMessage}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Перевірити підключення")
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun normalizeBaseUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
}
