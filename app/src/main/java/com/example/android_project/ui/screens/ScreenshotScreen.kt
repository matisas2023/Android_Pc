package com.example.android_project.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import kotlinx.coroutines.launch

@Composable
fun ScreenshotScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = remember(serverIp) { normalizeBaseUrl(serverIp) }
    val api = remember(baseUrl, token) {
        baseUrl?.let { ApiFactory.create(it, token) }
    }
    var status by remember { mutableStateOf("") }
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Скріншот", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = onBack) { Text("Назад") }

        Button(
            onClick = {
                scope.launch {
                    if (api == null) {
                        status = "Налаштуйте сервер"
                        return@launch
                    }
                    status = "Завантаження..."
                    runCatching { api.screenshot() }
                        .onSuccess { response ->
                            if (response.isSuccessful) {
                                val bytes = response.body()?.bytes() ?: ByteArray(0)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageBitmap = bitmap?.asImageBitmap()
                                status = if (bitmap != null) "Готово" else "Не вдалося декодувати"
                            } else {
                                status = "Помилка: ${response.code()}"
                            }
                        }
                        .onFailure { error ->
                            status = "Помилка: ${error.localizedMessage}"
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Отримати скріншот")
        }

        imageBitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Скріншот",
                modifier = Modifier
                    .fillMaxWidth()
                    .size(280.dp),
            )
        }

        if (status.isNotEmpty()) {
            Text(text = status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
