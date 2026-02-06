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
import com.example.android_project.network.ServerDiscovery
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
        if (serverIp.isBlank()) {
            statusMessage = "Пошук сервера..."
            val discovered = ServerDiscovery.discover()
            if (discovered != null) {
                serverIp = resolveServerAddress(discovered)
                token = discovered.token.orEmpty().ifBlank { "change-me" }
                settingsRepository.saveSettings(serverIp, token)
                statusMessage = "Підключення знайдено"
                onContinue()
            } else {
                statusMessage = "Сервер не знайдено"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Підключення", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Підключення відбувається автоматично, без введення IP та token.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = serverIp,
            onValueChange = { serverIp = it },
            label = { Text("Адреса сервера (IP:port)") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token (якщо потрібно)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                scope.launch {
                    val baseUrl = normalizeBaseUrl(serverIp)
                    if (baseUrl == null) {
                        statusMessage = "Не вдалося сформувати адресу"
                        return@launch
                    }
                    statusMessage = "Перевірка..."
                    val effectiveToken = token.ifBlank { "change-me" }
                    runCatching {
                        val api = ApiFactory.create(baseUrl, effectiveToken)
                        api.auth(AuthRequest(effectiveToken))
                    }.onSuccess { response ->
                        statusMessage = if (response.isSuccessful) {
                            settingsRepository.saveSettings(serverIp, effectiveToken)
                            onContinue()
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
            Text("Підключитися вручну")
        }

        Button(
            onClick = {
                scope.launch {
                    statusMessage = "Пошук сервера..."
                    val discovered = ServerDiscovery.discover()
                    if (discovered == null) {
                        statusMessage = "Сервер не знайдено"
                        return@launch
                    }
                    serverIp = resolveServerAddress(discovered)
                    token = discovered.token.orEmpty()
                    settingsRepository.saveSettings(serverIp, token)
                    statusMessage = "Перевірка..."
                    val baseUrl = normalizeBaseUrl(serverIp)
                    if (baseUrl == null) {
                        statusMessage = "Не вдалося сформувати адресу"
                        return@launch
                    }
                    val effectiveToken = token.ifBlank { "change-me" }
                    runCatching {
                        val api = ApiFactory.create(baseUrl, effectiveToken)
                        api.auth(AuthRequest(effectiveToken))
                    }.onSuccess { response ->
                        statusMessage = if (response.isSuccessful) {
                            settingsRepository.saveSettings(serverIp, effectiveToken)
                            onContinue()
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
            Text("Автоматично підключитися")
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

private fun resolveServerAddress(discovered: com.example.android_project.network.DiscoveredServer): String {
    return discovered.tunnelUrl
        ?: discovered.externalUrl
        ?: "${discovered.host}:${discovered.port}"
}
