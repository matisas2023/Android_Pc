package com.example.android_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import com.example.android_project.network.MouseClickRequest
import com.example.android_project.network.MouseMoveRequest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MouseScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = remember(serverIp) { normalizeBaseUrl(serverIp) }
    val api = remember(baseUrl, token) {
        baseUrl?.let { ApiFactory.create(it, token) }
    }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Керування мишею", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Назад") }
        }

        Text(text = "Трекпад", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                )
                .pointerInput(api) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (api == null) {
                            status = "Налаштуйте сервер"
                        } else {
                            scope.launch {
                                status = sendMouseMove(
                                    api,
                                    dragAmount.x.roundToInt(),
                                    dragAmount.y.roundToInt(),
                                )
                            }
                        }
                    }
                },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Віртуальні кнопки", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendMouseClick(it, "left") } ?: "Налаштуйте сервер"
                    }
                },
                modifier = Modifier.fillMaxWidth(0.5f),
            ) {
                Text("ЛКМ")
            }
            Button(
                onClick = {
                    scope.launch {
                        status = api?.let { sendMouseClick(it, "right") } ?: "Налаштуйте сервер"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ПКМ")
            }
        }

        if (status.isNotEmpty()) {
            Text(text = status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun sendMouseMove(api: com.example.android_project.network.ApiService, x: Int, y: Int): String {
    return runCatching {
        api.mouseMove(MouseMoveRequest(x = x, y = y, absolute = false))
    }.fold(
        onSuccess = { if (it.isSuccessful) "Готово" else "Помилка: ${it.code()}" },
        onFailure = { "Помилка: ${it.localizedMessage}" },
    )
}

private suspend fun sendMouseClick(
    api: com.example.android_project.network.ApiService,
    button: String,
): String {
    return runCatching {
        api.mouseClick(MouseClickRequest(button = button))
    }.fold(
        onSuccess = { if (it.isSuccessful) "Клік виконано" else "Помилка: ${it.code()}" },
        onFailure = { "Помилка: ${it.localizedMessage}" },
    )
}
