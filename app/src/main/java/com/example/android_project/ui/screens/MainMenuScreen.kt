package com.example.android_project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.android_project.Routes

@Composable
fun MainMenuScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Головне меню") },
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

        Button(
            onClick = { navController.navigate(Routes.Session.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сесія користувача")
        }

        Button(
            onClick = { navController.navigate(Routes.Media.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Медіа (стрім/камера/запис)")
        }
    }
}
