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
 * Groq, Mistral, and many other providers expose an OpenAI-compatible `/chat/completions`
 * endpoint, so a single request/response shape covers all of them — only the endpoint URL, model
 * name, and provider label differ. See [GroqClient] / [MistralClient] for the thin wrappers that
 * configure this for each provider.
 */
class OpenAiCompatibleClient(
    private val endpoint: String,
    private val model: String,
    private val apiKeyProvider: () -> String
) : AiClient {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override val isConfigured: Boolean get() = apiKeyProvider().isNotBlank()

    @Serializable
    private data class Message(val role: String, val content: String)
    @Serializable
    private data class Request(val model: String, val messages: List<Message>, val temperature: Double = 0.3)

    @Serializable
    private data class Choice(val message: Message? = null)
    @Serializable
    private data class Response(val choices: List<Choice> = emptyList())

    override suspend fun complete(prompt: String): String? = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) return@withContext null
        runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 12_000
                readTimeout = 12_000
            }

            val body = json.encodeToString(
                Request.serializer(),
                Request(model = model, messages = listOf(Message(role = "user", content = prompt)))
            )
            conn.outputStream.use { os: OutputStream -> os.write(body.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != 200) return@withContext null

            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val parsed = json.decodeFromString(Response.serializer(), raw)
            parsed.choices.firstOrNull()?.message?.content?.trim()
        }.getOrNull()
    }
}
