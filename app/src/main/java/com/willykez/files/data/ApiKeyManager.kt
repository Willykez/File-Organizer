package com.willykez.files.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.willykez.files.data.model.AiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lets the user paste in their own API key per AI provider from Settings, at runtime, instead of
 * only being able to bake one in at build time via `local.properties` (Gemini only, as a
 * fallback — see MainViewModel). Stored in [EncryptedSharedPreferences] (AES-256, Android
 * Keystore-backed) — never in plain DataStore, never logged, and a raw value is never redisplayed
 * once saved (see [maskedPreview]).
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

    private val _configuredProviders = MutableStateFlow<Set<AiProvider>>(emptySet())
    val configuredProviders: StateFlow<Set<AiProvider>> get() = _configuredProviders

    /** Call once from an IO-dispatched coroutine at startup — deliberately not done in `init`,
     *  since that would run the first (synchronous, disk-touching) EncryptedSharedPreferences
     *  access on whatever thread constructs this class, which for an AndroidViewModel property
     *  is the main thread. Also migrates a key saved under the old single-provider scheme
     *  (pre-multi-provider) over to the Gemini slot, once. */
    fun refreshConfiguredProviders() {
        runCatching {
            val legacy = prefs.getString(LEGACY_KEY, null)
            if (!legacy.isNullOrBlank() && prefs.getString(prefKeyFor(AiProvider.GEMINI), null).isNullOrBlank()) {
                prefs.edit().putString(prefKeyFor(AiProvider.GEMINI), legacy).remove(LEGACY_KEY).apply()
            }
        }
        _configuredProviders.value = AiProvider.entries.filterTo(mutableSetOf()) {
            runCatching { !prefs.getString(prefKeyFor(it), null).isNullOrBlank() }.getOrDefault(false)
        }
    }

    fun getApiKey(provider: AiProvider): String? =
        runCatching { prefs.getString(prefKeyFor(provider), null) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun setApiKey(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        runCatching { prefs.edit().putString(prefKeyFor(provider), trimmed).apply() }
        _configuredProviders.value = if (trimmed.isNotBlank()) _configuredProviders.value + provider else _configuredProviders.value - provider
    }

    fun clearApiKey(provider: AiProvider) {
        runCatching { prefs.edit().remove(prefKeyFor(provider)).apply() }
        _configuredProviders.value = _configuredProviders.value - provider
    }

    /** Last 4 characters only — the full key is never shown again once saved. */
    fun maskedPreview(provider: AiProvider): String? {
        val key = getApiKey(provider) ?: return null
        return if (key.length <= 4) "••••" else "••••${key.takeLast(4)}"
    }

    private fun prefKeyFor(provider: AiProvider) = "api_key_${provider.name}"

    companion object {
        private const val LEGACY_KEY = "gemini_api_key"
    }
}
