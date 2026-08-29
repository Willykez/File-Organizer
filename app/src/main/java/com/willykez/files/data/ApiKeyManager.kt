package com.willykez.files.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lets the user paste in their own Gemini API key from Settings, at runtime, instead of only
 * being able to bake one in at build time via `local.properties`. Stored in
 * [EncryptedSharedPreferences] (AES-256, Android Keystore-backed) — never in plain DataStore,
 * never logged, and the raw value is never redisplayed once saved (see [maskedPreview]).
 *
 * Call sites should do disk I/O off the main thread — [MainViewModel] wraps every call here in a
 * coroutine, since `EncryptedSharedPreferences.create` touches disk on first use.
 */
class ApiKeyManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _hasKey = MutableStateFlow(false)
    val hasKey: StateFlow<Boolean> get() = _hasKey

    /** Call once from an IO-dispatched coroutine at startup — deliberately not done in `init`,
     *  since that would run the first (synchronous, disk-touching) EncryptedSharedPreferences
     *  access on whatever thread constructs this class, which for an AndroidViewModel property
     *  is the main thread. */
    fun refreshHasKey() {
        _hasKey.value = runCatching { !prefs.getString(KEY, null).isNullOrBlank() }.getOrDefault(false)
    }

    fun getApiKey(): String? = runCatching { prefs.getString(KEY, null) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        runCatching { prefs.edit().putString(KEY, trimmed).apply() }
        _hasKey.value = trimmed.isNotBlank()
    }

    fun clearApiKey() {
        runCatching { prefs.edit().remove(KEY).apply() }
        _hasKey.value = false
    }

    /** Last 4 characters only — the full key is never shown again once saved. */
    fun maskedPreview(): String? {
        val key = getApiKey() ?: return null
        return if (key.length <= 4) "••••" else "••••${key.takeLast(4)}"
    }

    companion object {
        private const val KEY = "gemini_api_key"
    }
}
