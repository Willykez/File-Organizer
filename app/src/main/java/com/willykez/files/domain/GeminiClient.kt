package com.willykez.files.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin client for Gemini's `generateContent` endpoint, used only to make the AI Chat tab's
 * replies more conversational. Entirely optional: [CommandParser] and [CommandExecutor] work
 * with zero network access, so a missing/blank [apiKey] just means chat falls back to canned,
 * locally-computed responses (see MainViewModel.fallbackChatReply) instead of failing the feature.
 *
 * Security note: the previous version of this app had a live API key hardcoded directly in the
 * source (committed to git history). This client takes the key as a constructor parameter — it
 * is wired up from `BuildConfig.GEMINI_API_KEY`, itself sourced from a gitignored
 * `local.properties` entry or a CI secret, never from source code.
 */
class GeminiClient(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    @Serializable
    private data class Part(val text: String)
    @Serializable
    private data class Content(val parts: List<Part>)
    @Serializable
    private data class GenerationConfig(val temperature: Double = 0.3, val topP: Double = 0.8, val topK: Int = 20)
    @Serializable
    private data class Request(val contents: List<Content>, val generationConfig: GenerationConfig = GenerationConfig())

    @Serializable
    private data class Candidate(val content: Content? = null)
    @Serializable
    private data class Response(val candidates: List<Candidate> = emptyList())

    /** Returns the model's reply, or null on any failure (timeout, non-200, missing key, etc). */
    suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        runCatching {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                doOutput = true
                connectTimeout = 12_000
                readTimeout = 12_000
            }

            val body = json.encodeToString(
                Request.serializer(),
                Request(contents = listOf(Content(parts = listOf(Part(text = prompt)))))
            )
            conn.outputStream.use { os: OutputStream -> os.write(body.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != 200) return@withContext null

            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val parsed = json.decodeFromString(Response.serializer(), raw)
            parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        }.getOrNull()
    }
}
