package com.example.android_project.ui.screens

import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import com.example.android_project.network.ScreenRecordStartRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    var showFullScreen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Медіа") },
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
                SectionCard(title = "Трансляція") {
                    OutlinedTextField(
                        value = fps,
                        onValueChange = { fps = it },
                        label = { Text("FPS") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = quality,
                        onValueChange = { quality = it },
                        label = { Text("Якість (30-95, камера)") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = deviceIndex,
                        onValueChange = { deviceIndex = it },
                        label = { Text("Camera index") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = { streamType = StreamType.SCREEN },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Екран")
                        }
                        FilledTonalButton(
                            onClick = { streamType = StreamType.CAMERA },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Камера")
                        }
                        FilledTonalButton(
                            onClick = { streamType = StreamType.NONE },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Стоп")
                        }
                    }

                    if (streamType != StreamType.NONE && baseUrl != null) {
                        FilledTonalButton(
                            onClick = { showFullScreen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("На весь екран")
                        }
                        val fpsValue = fps.toIntOrNull() ?: 5
                        val qualityValue = quality.toIntOrNull() ?: 80
                        val deviceValue = deviceIndex.toIntOrNull() ?: 0
                        val streamUrl = when (streamType) {
                            StreamType.SCREEN -> "${baseUrl}screen/stream?fps=$fpsValue"
                            StreamType.CAMERA -> "${baseUrl}camera/stream?fps=$fpsValue&quality=$qualityValue&device_index=$deviceValue"
                            StreamType.NONE -> ""
                        }
                        MjpegStreamView(
                            url = streamUrl,
                            token = token,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Фото з камери") {
                    FilledTonalButton(
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
                                            statusMessage = if (bitmap != null) {
                                                "Фото отримано"
                                            } else {
                                                "Не вдалося декодувати"
                                            }
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
                        Text("Зробити фото")
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
                }
            }

            item {
                SectionCard(title = "Запис екрана") {
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
                        FilledTonalButton(
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
                                            ScreenRecordStartRequest(fps = fpsValue, durationSeconds = durationValue),
                                        )
                                    }.onSuccess { response ->
                                        if (response.isSuccessful) {
                                            recordingId = response.body()?.recordingId.orEmpty()
                                            statusMessage = "Запис запущено"
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
                                    if (recordingId.isBlank()) {
                                        statusMessage = "Немає recording_id"
                                        return@launch
                                    }
                                    statusMessage = "Зупинка..."
                                    runCatching { api.screenRecordStop(recordingId) }
                                        .onSuccess { response ->
                                            statusMessage = if (response.isSuccessful) {
                                                "Запис зупинено"
                                            } else {
                                                "Помилка: ${response.code()}"
                                            }
                                        }
                                        .onFailure { error ->
                                            statusMessage = "Помилка: ${error.localizedMessage}"
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Стоп")
                        }
                    }

                    FilledTonalButton(
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
                }
            }

            if (statusMessage.isNotEmpty()) {
                item {
                    Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showFullScreen && streamType != StreamType.NONE && baseUrl != null) {
        val fpsValue = fps.toIntOrNull() ?: 5
        val qualityValue = quality.toIntOrNull() ?: 80
        val deviceValue = deviceIndex.toIntOrNull() ?: 0
        val streamUrl = when (streamType) {
            StreamType.SCREEN -> "${baseUrl}screen/stream?fps=$fpsValue"
            StreamType.CAMERA -> "${baseUrl}camera/stream?fps=$fpsValue&quality=$qualityValue&device_index=$deviceValue"
            StreamType.NONE -> ""
        }

        Dialog(onDismissRequest = { showFullScreen = false }) {
            Box(modifier = Modifier.fillMaxSize()) {
                MjpegStreamView(
                    url = streamUrl,
                    token = token,
                    modifier = Modifier.fillMaxSize(),
                )
                FilledTonalButton(
                    onClick = { showFullScreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Text("Закрити")
                }
            }
        }
    }
}

@Composable
private fun MjpegStreamView(
    url: String,
    token: String,
    modifier: Modifier = Modifier,
) {
    val effectiveToken = token.ifBlank { "change-me" }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }
        },
        update = { webView ->
            webView.loadUrl(
                url,
                mapOf(
                    "X-API-Token" to effectiveToken,
                    "Authorization" to "Bearer $effectiveToken",
                ),
            )
        },
        modifier = modifier,
    )
}

private enum class StreamType {
    NONE,
    SCREEN,
    CAMERA,
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
