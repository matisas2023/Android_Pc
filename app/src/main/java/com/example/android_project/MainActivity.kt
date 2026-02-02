package com.example.android_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.android_project.data.SettingsRepository
import com.example.android_project.ui.screens.KeyboardScreen
import com.example.android_project.ui.screens.LoginScreen
import com.example.android_project.ui.screens.MainMenuScreen
import com.example.android_project.ui.screens.MouseScreen
import com.example.android_project.ui.screens.ScreenshotScreen
import com.example.android_project.ui.screens.SystemScreen
import com.example.android_project.ui.theme.Android_projectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Android_projectTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val settingsRepository = remember { SettingsRepository(applicationContext) }
                    val navController = rememberNavController()
                    AppNavHost(navController = navController, settingsRepository = settingsRepository)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    settingsRepository: SettingsRepository,
) {
    NavHost(navController = navController, startDestination = Routes.Login.route) {
        composable(Routes.Login.route) {
            LoginScreen(
                settingsRepository = settingsRepository,
                onContinue = { navController.navigate(Routes.MainMenu.route) },
            )
        }
        composable(Routes.MainMenu.route) {
            MainMenuScreen(navController = navController)
        }
        composable(Routes.Mouse.route) {
            MouseScreen(settingsRepository = settingsRepository, onBack = { navController.popBackStack() })
        }
        composable(Routes.Keyboard.route) {
            KeyboardScreen(settingsRepository = settingsRepository, onBack = { navController.popBackStack() })
        }
        composable(Routes.System.route) {
            SystemScreen(settingsRepository = settingsRepository, onBack = { navController.popBackStack() })
        }
        composable(Routes.Screenshot.route) {
            ScreenshotScreen(settingsRepository = settingsRepository, onBack = { navController.popBackStack() })
        }
    }
}

sealed class Routes(val route: String) {
    data object Login : Routes("login")
    data object MainMenu : Routes("main_menu")
    data object Mouse : Routes("mouse")
    data object Keyboard : Routes("keyboard")
    data object System : Routes("system")
    data object Screenshot : Routes("screenshot")
}
