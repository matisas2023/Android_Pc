package com.pcremote.client.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {
    private val key = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pc_remote_secure",
        key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveServer(host: String, token: String, name: String) {
        prefs.edit()
            .putString("host", host)
            .putString("token", token)
            .putString("name", name)
            .apply()
    }

    fun host(): String = prefs.getString("host", "") ?: ""
    fun token(): String = prefs.getString("token", "") ?: ""
    fun name(): String = prefs.getString("name", "My PC") ?: "My PC"
}
