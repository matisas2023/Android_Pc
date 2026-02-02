package com.example.android_project.ui.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.core.app.NotificationCompat
import com.example.android_project.data.SettingsRepository
import com.example.android_project.network.ApiFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.android_project.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    navController: NavHostController,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    val serverIp by settingsRepository.serverIpFlow.collectAsState(initial = "")
    val token by settingsRepository.tokenFlow.collectAsState(initial = "")
    val baseUrl = normalizeBaseUrl(serverIp)
    val api = baseUrl?.let { ApiFactory.create(it, token) }
    var isOnline by remember { mutableStateOf(false) }
    var lastOnlineState by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(baseUrl, token) {
        while (isActive) {
            val online = runCatching {
                api?.health()?.isSuccessful == true
            }.getOrDefault(false)

            isOnline = online
            if (lastOnlineState != null && lastOnlineState != online) {
                sendStatusNotification(context, online)
            }
            lastOnlineState = online
            delay(5000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Головне меню") },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (isOnline) "ПК онлайн" else "ПК оффлайн",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFD32F2F),
                                    shape = CircleShape,
                                ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MenuItemCard(title = "Керування мишею") {
                    navController.navigate(Routes.Mouse.route)
                }
            }
            item {
                MenuItemCard(title = "Керування клавіатурою") {
                    navController.navigate(Routes.Keyboard.route)
                }
            }
            item {
                MenuItemCard(title = "Система") {
                    navController.navigate(Routes.System.route)
                }
            }
            item {
                MenuItemCard(title = "Скріншот") {
                    navController.navigate(Routes.Screenshot.route)
                }
            }
            item {
                MenuItemCard(title = "Сесія користувача") {
                    navController.navigate(Routes.Session.route)
                }
            }
            item {
                MenuItemCard(title = "Медіа (стрім/камера/запис)") {
                    navController.navigate(Routes.Media.route)
                }
            }
        }
    }
}

private fun sendStatusNotification(context: Context, online: Boolean) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "pc_status"
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "PC статус",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    val title = if (online) "ПК увімкнено" else "ПК вимкнено"
    val message = if (online) "Сервер доступний" else "Сервер недоступний"

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .build()

    manager.notify(1001, notification)
}

@Composable
private fun MenuItemCard(title: String, onClick: () -> Unit) {
    ElevatedCard {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
