package com.pcremote.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pcremote.client.di.AppContainer
import com.pcremote.client.ui.screens.RootScreen
import com.pcremote.client.ui.viewmodel.MainViewModel
import com.pcremote.client.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = remember { AppContainer(applicationContext) }
            val vm: MainViewModel = viewModel(factory = MainViewModelFactory(container.remoteRepository, container.secureStore))
            Surface(color = MaterialTheme.colorScheme.background) {
                RootScreen(vm)
            }
        }
    }
}
