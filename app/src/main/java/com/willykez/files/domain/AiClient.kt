package com.willykez.files.domain

/**
 * Common surface every provider client implements, so the rest of the app (chat replies, custom-
 * command parsing) never needs to know which provider is active — see [AiClientFactory].
 */
interface AiClient {
    val isConfigured: Boolean

    /** Returns the model's reply, or null on any failure (timeout, non-200, missing key, etc). */
    suspend fun complete(prompt: String): String?

    /** Lightweight connectivity check for the Settings screen's "Test Connection" button. */
    suspend fun testConnection(): Result<Unit> {
        val reply = complete("Reply with exactly: OK")
        return if (reply != null) Result.success(Unit)
        else Result.failure(IllegalStateException("No response — check the key and your connection"))
    }
}
