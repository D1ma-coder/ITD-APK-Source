package com.itd.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.UUID

class TokenStore(context: Context) : SessionStorage {
    private val context = context.applicationContext
    private val preferences: SharedPreferences = openPreferences()

    @Volatile
    override var accessToken: String? = null

    override var refreshToken: String?
        get() = preferences.getString("refresh_token", null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("refresh_token") else putString("refresh_token", value)
            }.apply()
        }

    override val deviceId: String
        @Synchronized get() {
            return preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("device_id", it).apply()
            }
        }

    override fun clear() {
        accessToken = null
        refreshToken = null
    }

    @Suppress("DEPRECATION")
    private fun encryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "itd_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun openPreferences(): SharedPreferences {
        return try {
            encryptedPreferences()
        } catch (_: Exception) {
            context.getSharedPreferences("itd_auth_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            runCatching { File(context.applicationInfo.dataDir, "shared_prefs/itd_auth_prefs.xml").delete() }
            try {
                encryptedPreferences()
            } catch (_: Exception) {
                context.getSharedPreferences("itd_auth_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
