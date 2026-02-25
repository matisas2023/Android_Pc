package com.example.android_project.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import com.example.android_project.network.SystemPowerRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityControlScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = remember(serverIp) { normalizeBaseUrl(serverIp) }
    val api = remember(baseUrl, token) { baseUrl?.let { ApiFactory.create(it, token) } }

    var status by remember { mutableStateOf("") }
    var screenshotImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var cameraImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var cameraIndex by remember { mutableStateOf("0") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Швидкий контроль ПК") },
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
                SectionCard(title = "1) Скріншот екрана") {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val apiService = api
                                if (apiService == null) {
                                    status = "Налаштуйте сервер"
                                    return@launch
                                }
                                status = "Завантаження скріншота..."
                                runCatching { apiService.screenshot() }
                                    .onSuccess { response ->
                                        if (!response.isSuccessful) {
                                            status = "Скріншот: помилка ${response.code()}"
                                            return@onSuccess
                                        }

                                        val body = response.body()
                                        val bytes = body?.bytes() ?: ByteArray(0)
                                        val bitmap = decodeBitmapSafely(bytes)
                                        if (bitmap == null) {
                                            status = "Скріншот: помилка декодування"
                                        } else {
                                            screenshotImage = bitmap.asImageBitmap()
                                            status = "Скріншот отримано"
                                        }
                                    }
                                    .onFailure { error ->
                                        status = "Скріншот: ${error.localizedMessage}"
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Отримати скріншот")
                    }

                    screenshotImage?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = "Скріншот екрана",
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(220.dp),
                        )
                    }
                }
            }

            item {
                SectionCard(title = "2) Фото з камери ПК") {
                    OutlinedTextField(
                        value = cameraIndex,
                        onValueChange = { cameraIndex = it },
                        label = { Text("Індекс камери") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val apiService = api
                                if (apiService == null) {
                                    status = "Налаштуйте сервер"
                                    return@launch
                                }

                                status = "Отримання фото з камери..."
                                val index = cameraIndex.toIntOrNull() ?: 0
                                runCatching { apiService.cameraPhoto(deviceIndex = index, quality = 90) }
                                    .onSuccess { response ->
                                        if (!response.isSuccessful) {
                                            status = "Камера: помилка ${response.code()}"
                                            return@onSuccess
                                        }

                                        val body = response.body()
                                        val bytes = body?.bytes() ?: ByteArray(0)
                                        val bitmap = decodeBitmapSafely(bytes)
                                        if (bitmap == null) {
                                            status = "Камера: помилка декодування"
                                        } else {
                                            cameraImage = bitmap.asImageBitmap()
                                            status = "Фото з камери отримано"
                                        }
                                    }
                                    .onFailure { error ->
                                        status = "Камера: ${error.localizedMessage}"
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
                                .size(220.dp),
                        )
                    }
                }
            }

            item {
                SectionCard(title = "3) Управління живленням ПК") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    status = sendPowerAction(api, "shutdown")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Shutdown") }

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    status = sendPowerAction(api, "restart")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Restart") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    status = sendPowerAction(api, "sleep")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Sleep") }

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    status = sendPowerAction(api, "lock")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Lock") }
                    }
                }
            }

            if (status.isNotBlank()) {
                item {
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private suspend fun sendPowerAction(
    api: com.example.android_project.network.ApiService?,
    action: String,
): String {
    val apiService = api ?: return "Налаштуйте сервер"
    return runCatching {
        apiService.systemPower(SystemPowerRequest(action = action))
    }.fold(
        onSuccess = { response ->
            if (response.isSuccessful) {
                "Команду $action відправлено"
            } else {
                "Живлення: помилка ${response.code()}"
            }
        },
        onFailure = { error ->
            "Живлення: ${error.localizedMessage}"
        },
    )
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

private fun decodeBitmapSafely(bytes: ByteArray): android.graphics.Bitmap? {
    if (bytes.isEmpty()) return null
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}
