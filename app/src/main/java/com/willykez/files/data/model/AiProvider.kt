package com.willykez.files.data.model

/**
 * The AI providers the user can choose between in Settings for the AI Chat tab's "smart parsing"
 * mode. None of these are required — everything works offline via [com.willykez.files.domain.CommandParser]
 * and [com.willykez.files.domain.CustomCommandParser]'s heuristic path without any provider configured.
 */
enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val keySignupUrl: String,
    val description: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        keySignupUrl = "https://aistudio.google.com/apikey",
        description = "Fast, generous free tier."
    ),
    GROQ(
        displayName = "Groq",
        defaultModel = "llama-3.3-70b-versatile",
        keySignupUrl = "https://console.groq.com/keys",
        description = "Very low latency, open-weight models."
    ),
    MISTRAL(
        displayName = "Mistral",
        defaultModel = "mistral-small-latest",
        keySignupUrl = "https://console.mistral.ai/api-keys",
        description = "Strong multilingual support."
    );

    companion object {
        val default = GEMINI
    }
}
