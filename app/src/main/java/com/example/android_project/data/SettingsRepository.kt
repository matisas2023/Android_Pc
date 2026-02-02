package com.example.android_project.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val serverIpKey = stringPreferencesKey("server_ip")
    private val tokenKey = stringPreferencesKey("api_token")

    val serverIpFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[serverIpKey].orEmpty()
    }

    val tokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[tokenKey].orEmpty()
    }

    suspend fun saveServerIp(value: String) {
        context.dataStore.edit { prefs -> prefs[serverIpKey] = value }
    }

    suspend fun saveToken(value: String) {
        context.dataStore.edit { prefs -> prefs[tokenKey] = value }
    }

    suspend fun saveSettings(serverIp: String, token: String) {
        context.dataStore.edit { prefs: Preferences ->
            prefs[serverIpKey] = serverIp
            prefs[tokenKey] = token
        }
    }
}
