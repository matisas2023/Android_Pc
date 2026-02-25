package com.pcremote.client.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pcremote.client.data.repository.RemoteRepository
import com.pcremote.client.security.SecureStore

class MainViewModelFactory(
    private val repo: RemoteRepository,
    private val secureStore: SecureStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repo, secureStore) as T
    }
}
