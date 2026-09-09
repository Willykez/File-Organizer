package com.willykez.files.domain

import com.willykez.files.data.model.AiProvider

/**
 * Builds the [AiClient] for whichever provider is currently selected. Kept as a factory (not a
 * cached instance) because the active provider — and its key — can change at runtime from
 * Settings; callers should ask for a fresh client each time they need one rather than holding a
 * reference across a provider switch.
 */
object AiClientFactory {

    fun create(provider: AiProvider, apiKeyProvider: () -> String): AiClient = when (provider) {
        AiProvider.GEMINI -> GeminiClient(apiKeyProvider)
        AiProvider.GROQ -> OpenAiCompatibleClient(
            endpoint = "https://api.groq.com/openai/v1/chat/completions",
            model = AiProvider.GROQ.defaultModel,
            apiKeyProvider = apiKeyProvider
        )
        AiProvider.MISTRAL -> OpenAiCompatibleClient(
            endpoint = "https://api.mistral.ai/v1/chat/completions",
            model = AiProvider.MISTRAL.defaultModel,
            apiKeyProvider = apiKeyProvider
        )
    }
}
