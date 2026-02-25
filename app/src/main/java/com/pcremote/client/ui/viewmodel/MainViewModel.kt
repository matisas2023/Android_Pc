package com.pcremote.client.ui.viewmodel

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcremote.client.data.repository.RemoteRepository
import com.pcremote.client.domain.model.StatusResponse
import com.pcremote.client.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class UiState(
    val host: String = "",
    val code: String = "",
    val token: String = "",
    val statusText: String = "",
    val paired: Boolean = false,
    val dashboard: StatusResponse? = null,
    val screenshot: ImageBitmap? = null,
    val camera: ImageBitmap? = null,
)

class MainViewModel(
    private val repo: RemoteRepository,
    private val secureStore: SecureStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        UiState(
            host = secureStore.host(),
            token = secureStore.token(),
            paired = secureStore.token().isNotBlank(),
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun updateHost(v: String) { _ui.value = _ui.value.copy(host = v) }
    fun updateCode(v: String) { _ui.value = _ui.value.copy(code = v) }

    fun autoConnect() {
        viewModelScope.launch {
            val host = normalize(_ui.value.host)
            if (host == null) {
                _ui.value = _ui.value.copy(statusText = "Невірний host")
                return@launch
            }

            _ui.value = _ui.value.copy(statusText = "Автопідключення: запит pairing code...")
            repo.autoPair(host, "android-auto")
                .onSuccess { token ->
                    secureStore.saveServer(host, token, "My PC")
                    _ui.value = _ui.value.copy(
                        token = token,
                        paired = true,
                        host = host,
                        statusText = "Автопідключення успішне",
                    )
                    delay(300)
                    loadStatus()
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(statusText = "Автопідключення помилка: ${err.message}")
                }
        }
    }

    fun pair() {
        viewModelScope.launch {
            val host = normalize(_ui.value.host)
            if (host == null) {
                _ui.value = _ui.value.copy(statusText = "Невірний host")
                return@launch
            }

            repo.pair(host, _ui.value.code, "android-client")
                .onSuccess { token ->
                    secureStore.saveServer(host, token, "My PC")
                    _ui.value = _ui.value.copy(token = token, paired = true, host = host, statusText = "Pairing OK")
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(statusText = "Pairing error: ${err.message}")
                }
        }
    }

    fun loadStatus() {
        viewModelScope.launch {
            val host = normalize(_ui.value.host) ?: return@launch
            repo.status(host, _ui.value.token)
                .onSuccess { st -> _ui.value = _ui.value.copy(dashboard = st, statusText = "Status loaded") }
                .onFailure { err -> _ui.value = _ui.value.copy(statusText = "Status error: ${err.message}") }
        }
    }

    fun screenshot() {
        viewModelScope.launch {
            val host = normalize(_ui.value.host) ?: return@launch
            repo.screenshot(host, _ui.value.token)
                .onSuccess { bytes ->
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    _ui.value = _ui.value.copy(screenshot = bmp?.asImageBitmap(), statusText = "Screenshot done")
                }
                .onFailure { err -> _ui.value = _ui.value.copy(statusText = "Screenshot error: ${err.message}") }
        }
    }

    fun cameraPhoto() {
        viewModelScope.launch {
            val host = normalize(_ui.value.host) ?: return@launch
            repo.cameraPhoto(host, _ui.value.token)
                .onSuccess { bytes ->
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    _ui.value = _ui.value.copy(camera = bmp?.asImageBitmap(), statusText = "Camera photo done")
                }
                .onFailure { err -> _ui.value = _ui.value.copy(statusText = "Camera error: ${err.message}") }
        }
    }

    fun power(action: String) {
        viewModelScope.launch {
            val host = normalize(_ui.value.host) ?: return@launch
            repo.power(host, _ui.value.token, action)
                .onSuccess { _ui.value = _ui.value.copy(statusText = "Power action sent: $action") }
                .onFailure { err -> _ui.value = _ui.value.copy(statusText = "Power error: ${err.message}") }
        }
    }

    private fun normalize(raw: String): String? {
        val t = raw.trim()
        if (t.isBlank()) return null
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }
}
