package com.pcremote.client.di

import android.content.Context
import com.pcremote.client.data.repository.RemoteRepository
import com.pcremote.client.security.SecureStore

class AppContainer(context: Context) {
    val secureStore = SecureStore(context)
    val remoteRepository = RemoteRepository()
}
