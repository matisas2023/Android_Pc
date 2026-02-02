package com.example.android_project.ui.screens

import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import com.example.android_project.network.ScreenRecordStartRequest
import kotlinx.coroutines.launch

@Composable
fun MediaScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = remember(serverIp) { normalizeBaseUrl(serverIp) }
    val api = remember(baseUrl, token) {
        baseUrl?.let { ApiFactory.create(it, token) }
    }
    var streamType by remember { mutableStateOf(StreamType.NONE) }
    var statusMessage by remember { mutableStateOf("") }
    var cameraImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var fps by remember { mutableStateOf("5") }
    var quality by remember { mutableStateOf("80") }
    var deviceIndex by remember { mutableStateOf("0") }
    var recordFps by remember { mutableStateOf("10") }
    var recordDuration by remember { mutableStateOf("") }
    var recordingId by remember { mutableStateOf("") }
    var recordingsSummary by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Медіа", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = onBack) { Text("Назад") }

        OutlinedTextField(
            value = fps,
            onValueChange = { fps = it },
            label = { Text("FPS") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = quality,
            onValueChange = { quality = it },
            label = { Text("Якість (30-95)") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = deviceIndex,
            onValueChange = { deviceIndex = it },
            label = { Text("Camera index") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { streamType = StreamType.SCREEN },
            ) {
                Text("Трансляція екрана")
            }
            Button(
                onClick = { streamType = StreamType.CAMERA },
            ) {
                Text("Трансляція камери")
            }
            Button(
                onClick = { streamType = StreamType.NONE },
            ) {
                Text("Зупинити")
            }
        }

        if (streamType != StreamType.NONE && baseUrl != null) {
            val fpsValue = fps.toIntOrNull() ?: 5
            val qualityValue = quality.toIntOrNull() ?: 80
            val deviceValue = deviceIndex.toIntOrNull() ?: 0
            val streamUrl = when (streamType) {
                StreamType.SCREEN -> "${baseUrl}screen/stream?fps=$fpsValue&quality=$qualityValue"
                StreamType.CAMERA -> "${baseUrl}camera/stream?fps=$fpsValue&quality=$qualityValue&device_index=$deviceValue"
                StreamType.NONE -> ""
            }
            MjpegStreamView(url = streamUrl, token = token)
        }

        Button(
            onClick = {
                scope.launch {
                    if (api == null) {
                        statusMessage = "Налаштуйте сервер"
                        return@launch
                    }
                    statusMessage = "Фото..."
                    runCatching { api.cameraPhoto() }
                        .onSuccess { response ->
                            if (response.isSuccessful) {
                                val bytes = response.body()?.bytes() ?: ByteArray(0)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                cameraImage = bitmap?.asImageBitmap()
                                statusMessage = if (bitmap != null) "Фото отримано" else "Не вдалося декодувати"
                            } else {
                                statusMessage = "Помилка: ${response.code()}"
                            }
                        }
                        .onFailure { error ->
                            statusMessage = "Помилка: ${error.localizedMessage}"
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Фото з камери")
        }

        cameraImage?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Фото з камери",
                modifier = Modifier
                    .fillMaxWidth()
                    .size(240.dp),
            )
        }

        Text(text = "Запис екрана", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = recordFps,
            onValueChange = { recordFps = it },
            label = { Text("FPS запису") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = recordDuration,
            onValueChange = { recordDuration = it },
            label = { Text("Тривалість (сек., опц.)") },
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
                        val fpsValue = recordFps.toIntOrNull() ?: 10
                        val durationValue = recordDuration.toIntOrNull()
                        statusMessage = "Старт запису..."
                        runCatching {
                            api.screenRecordStart(
                                ScreenRecordStartRequest(fps = fpsValue, duration_seconds = durationValue),
                            )
                        }.onSuccess { response ->
                            if (response.isSuccessful) {
                                recordingId = response.body()?.recording_id.orEmpty()
                                statusMessage = "Запис запущено"
                            } else {
                                statusMessage = "Помилка: ${response.code()}"
                            }
                        }.onFailure { error ->
                            statusMessage = "Помилка: ${error.localizedMessage}"
                        }
                    }
                },
            ) {
                Text("Старт запису")
            }

            Button(
                onClick = {
                    scope.launch {
                        if (api == null) {
                            statusMessage = "Налаштуйте сервер"
                            return@launch
                        }
                        if (recordingId.isBlank()) {
                            statusMessage = "Немає recording_id"
                            return@launch
                        }
                        statusMessage = "Зупинка..."
                        runCatching { api.screenRecordStop(recordingId) }
                            .onSuccess { response ->
                                statusMessage = if (response.isSuccessful) "Запис зупинено" else "Помилка: ${response.code()}"
                            }
                            .onFailure { error ->
                                statusMessage = "Помилка: ${error.localizedMessage}"
                            }
                    }
                },
            ) {
                Text("Стоп")
            }
        }

        Button(
            onClick = {
                scope.launch {
                    if (api == null) {
                        statusMessage = "Налаштуйте сервер"
                        return@launch
                    }
                    statusMessage = "Оновлення списку..."
                    runCatching { api.screenRecordings() }
                        .onSuccess { response ->
                            if (response.isSuccessful) {
                                val recordings = response.body()?.recordings.orEmpty()
                                recordingsSummary = recordings.entries.joinToString("\n") { (id, info) ->
                                    "$id • ${info.file} • ${if (info.completed) "готово" else "в процесі"}"
                                }
                                statusMessage = "Список оновлено"
                            } else {
                                statusMessage = "Помилка: ${response.code()}"
                            }
                        }
                        .onFailure { error ->
                            statusMessage = "Помилка: ${error.localizedMessage}"
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Оновити записи")
        }

        if (recordingId.isNotBlank()) {
            Text(text = "Recording ID: $recordingId")
        }

        if (recordingsSummary.isNotBlank()) {
            Text(text = recordingsSummary)
        }

        if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MjpegStreamView(url: String, token: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { webView ->
            webView.loadUrl(url, mapOf("X-API-Token" to token))
        },
        modifier = Modifier
            .fillMaxWidth()
            .size(240.dp),
    )
}

private enum class StreamType {
    NONE,
    SCREEN,
    CAMERA,
}
