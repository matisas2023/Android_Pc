package com.example.android_project.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.android_project.Routes

@Composable
fun MainMenuScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Головне меню", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = { navController.navigate(Routes.Mouse.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Керування мишею")
        }

        Button(
            onClick = { navController.navigate(Routes.Keyboard.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Керування клавіатурою")
        }

        Button(
            onClick = { navController.navigate(Routes.System.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Система")
        }

        Button(
            onClick = { navController.navigate(Routes.Screenshot.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Скріншот")
        }
    }
}
