package com.willykez.files.domain

import com.willykez.files.data.model.CustomAction
import com.willykez.files.data.model.CustomActionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

private val EXT_DOT_REGEX = Regex("""\.([a-zA-Z0-9]{2,4})\b""")
private val EXT_WORD_REGEX = Regex("""\b([a-zA-Z0-9]{2,4})\s+files\b""")
private val FILLER_WORDS = setOf(
    "the", "go", "to", "folder", "folders", "in", "from", "fetch", "for", "and", "move", "moves",
    "moving", "copy", "copying", "delete", "deleting", "remove", "removing", "alls", "all", "please",
    "a", "an", "of", "into", "onto", "on", "file", "files"
)
private val INTERNAL_HINTS = setOf("internal storage", "internal", "phone storage", "device storage", "phone memory")
private val SDCARD_HINTS = setOf("sd card", "sdcard", "external storage", "memory card", "micro sd")

object CustomCommandParser {

    /** Resolves a [CustomAction] to the concrete files it would touch — used to build the
     *  confirmation card, and to build the exact list the user approved before execution. */
    fun matchFiles(action: CustomAction, allMetadata: List<com.willykez.files.data.model.FileMetadata>): List<com.willykez.files.data.model.FileMetadata> {
        // Refuse to match "everything" — every parse path requires at least a source folder or an
        // extension filter before returning a non-null action, but this guard stays even if that
        // ever changes, since matching the whole device is never a reasonable outcome here.
        if (action.sourceFolderPath == null && action.extensions.isEmpty() && action.nameContains.isNullOrBlank()) {
            return emptyList()
        }
        var matches = allMetadata.asSequence()
        if (action.sourceFolderPath != null) {
            val prefix = action.sourceFolderPath
            matches = matches.filter { it.absolutePath == prefix || it.absolutePath.startsWith("$prefix/") }
        }
        if (action.extensions.isNotEmpty()) {
            matches = matches.filter { it.extension.lowercase(Locale.ROOT) in action.extensions }
        }
        if (!action.nameContains.isNullOrBlank()) {
            val needle = action.nameContains.lowercase(Locale.ROOT)
            matches = matches.filter { it.name.lowercase(Locale.ROOT).contains(needle) }
        }
        return matches.toList()
    }

    /** Best-effort offline parse. Returns null if the request isn't confidently actionable —
     *  callers should fall back to a plain conversational reply rather than guess. */
    fun parseOffline(
        text: String,
        volumes: List<StorageVolume>,
        availableFolders: Set<String>
    ): CustomAction? {
        val lower = text.lowercase(Locale.ROOT)
        val actionType = detectActionType(lower) ?: return null
        val extensions = extractExtensions(lower)

        // Strip extension mentions ("*.mkv", "mkv files") before guessing folder names, so an
        // extension token never leaks into a folder-name match.
        val sanitized = lower.replace(EXT_DOT_REGEX, " ").replace(EXT_WORD_REGEX, " ")
        val destPhrase = sanitized.substringAfterLast(" to ", "").trim()
        val sourcePhrase = if (destPhrase.isNotBlank()) sanitized.substringBeforeLast(" to ", sanitized) else sanitized

        val source = resolveFolder(sourcePhrase, volumes, availableFolders, requireExisting = true)

        if (actionType == CustomActionType.DELETE) {
            if (source == null && extensions.isEmpty()) return null
            return CustomAction(
                summary = buildSummary(actionType, extensions, source, null),
                actionType = actionType,
                sourceFolderPath = source?.first,
                sourceFolderLabel = source?.second,
                extensions = extensions,
                nameContains = null,
                destinationPath = null,
                destinationLabel = null
            )
        }

        if (destPhrase.isBlank()) return null
        val destination = resolveFolder(destPhrase, volumes, availableFolders, requireExisting = false) ?: return null

        return CustomAction(
            summary = buildSummary(actionType, extensions, source, destination),
            actionType = actionType,
            sourceFolderPath = source?.first,
            sourceFolderLabel = source?.second,
            extensions = extensions,
            nameContains = null,
            destinationPath = destination.first,
            destinationLabel = destination.second
        )
    }

    /** Optional enhancement: asks Gemini for short folder-name *hints* only (never a raw path),
     *  then resolves those hints through the same safe logic as [parseOffline]. Falls back to the
     *  offline parser if Gemini is unavailable or returns something we can't safely resolve. */
    suspend fun parseWithAi(
        text: String,
        gemini: GeminiClient,
        volumes: List<StorageVolume>,
        availableFolders: Set<String>
    ): CustomAction? {
        if (!gemini.isConfigured) return parseOffline(text, volumes, availableFolders)

        val prompt = buildString {
            appendLine("Extract a file-organizing instruction as STRICT JSON only, no other text, matching exactly:")
            appendLine("""{"actionType":"MOVE|COPY|DELETE|NONE","extensions":["ext",...],"sourceFolderHint":"name or null","sourceVolumeHint":"internal|sdcard|null","destinationFolderHint":"name or null","destinationVolumeHint":"internal|sdcard|null"}""")
            appendLine("extensions are lowercase without dots. Use \"NONE\" for actionType if the message isn't a file operation request.")
            append("Message: $text")
        }
        val raw = gemini.complete(prompt) ?: return parseOffline(text, volumes, availableFolders)
        val hint = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(AiActionHint.serializer(), raw.extractJsonObject())
        }.getOrNull() ?: return parseOffline(text, volumes, availableFolders)

        val actionType = when (hint.actionType.uppercase(Locale.ROOT)) {
            "MOVE" -> CustomActionType.MOVE
            "COPY" -> CustomActionType.COPY
            "DELETE" -> CustomActionType.DELETE
            else -> return null
        }
        val extensions = hint.extensions.map { it.lowercase(Locale.ROOT).removePrefix(".") }.toSet()

        val source = resolveHint(hint.sourceFolderHint, hint.sourceVolumeHint, volumes, availableFolders, requireExisting = true)
        val destination = if (actionType == CustomActionType.DELETE) null
        else resolveHint(hint.destinationFolderHint, hint.destinationVolumeHint, volumes, availableFolders, requireExisting = false)
            ?: return parseOffline(text, volumes, availableFolders)

        if (actionType == CustomActionType.DELETE && source == null && extensions.isEmpty()) {
            return parseOffline(text, volumes, availableFolders)
        }

        return CustomAction(
            summary = buildSummary(actionType, extensions, source, destination),
            actionType = actionType,
            sourceFolderPath = source?.first,
            sourceFolderLabel = source?.second,
            extensions = extensions,
            nameContains = null,
            destinationPath = destination?.first,
            destinationLabel = destination?.second
        )
    }

    // ---- shared resolution helpers ---------------------------------------------------------

    private fun detectActionType(lower: String): CustomActionType? = when {
        ("delete" in lower || "remove" in lower) && "move" !in lower && "copy" !in lower -> CustomActionType.DELETE
        "copy" in lower && "move" !in lower -> CustomActionType.COPY
        "move" in lower -> CustomActionType.MOVE
        else -> null
    }

    private fun extractExtensions(lower: String): Set<String> {
        val dotMatches = EXT_DOT_REGEX.findAll(lower).map { it.groupValues[1] }.toSet()
        if (dotMatches.isNotEmpty()) return dotMatches
        return EXT_WORD_REGEX.findAll(lower).map { it.groupValues[1] }.toSet()
    }

    /** Returns (absolutePath, displayLabel) or null if it couldn't be confidently resolved. */
    private fun resolveFolder(
        phrase: String, volumes: List<StorageVolume>, availableFolders: Set<String>, requireExisting: Boolean
    ): Pair<String, String>? {
        var remaining = phrase
        var volumeHint: String? = null
        for (h in INTERNAL_HINTS) if (remaining.contains(h)) { volumeHint = "internal"; remaining = remaining.replace(h, " ") }
        for (h in SDCARD_HINTS) if (remaining.contains(h)) { volumeHint = "sdcard"; remaining = remaining.replace(h, " ") }

        val folderNameGuess = remaining.split(Regex("\\s+"))
            .map { it.trim().trim('.', ',') }
            .filter { it.isNotBlank() && it !in FILLER_WORDS }
            .joinToString(" ")

        return resolveHint(folderNameGuess.ifBlank { null }, volumeHint, volumes, availableFolders, requireExisting)
    }

    private fun resolveHint(
        folderNameHint: String?, volumeHint: String?, volumes: List<StorageVolume>,
        availableFolders: Set<String>, requireExisting: Boolean
    ): Pair<String, String>? {
        val normalizedHint = volumeHint?.lowercase(Locale.ROOT)
        val volume = when (normalizedHint) {
            "internal" -> volumes.firstOrNull { it.isPrimary }
            "sdcard", "sd" -> volumes.firstOrNull { it.isRemovable } ?: return null // explicit SD card request but none present
            else -> null
        }

        if (folderNameHint.isNullOrBlank()) {
            // No specific subfolder named — only acceptable when we at least have a volume to
            // anchor to, and only for a destination (never fabricate an unscoped source).
            return if (!requireExisting && volume != null) volume.root.absolutePath to volume.label else null
        }

        if (requireExisting) {
            val candidates = availableFolders.filter { path ->
                val last = path.substringAfterLast('/')
                last.contains(folderNameHint, ignoreCase = true) || folderNameHint.contains(last, ignoreCase = true)
            }
            val scoped = if (volume != null) candidates.filter { it.startsWith(volume.root.absolutePath) } else candidates
            val best = (scoped.ifEmpty { candidates }).minByOrNull { it.length } ?: return null
            val label = best.substringAfterLast('/')
            return best to label
        }

        // Destination: doesn't need to exist yet — build it under the hinted (or primary) volume.
        val root = volume ?: volumes.firstOrNull { it.isPrimary } ?: volumes.firstOrNull() ?: return null
        val folderName = folderNameHint.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            word.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
        }
        return "${root.root.absolutePath}/$folderName" to "${root.label}/$folderName"
    }

    private fun buildSummary(
        actionType: CustomActionType, extensions: Set<String>, source: Pair<String, String>?, destination: Pair<String, String>?
    ): String {
        val what = if (extensions.isEmpty()) "files" else extensions.joinToString(", ") { ".$it" } + " files"
        val verb = when (actionType) {
            CustomActionType.MOVE -> "Move"
            CustomActionType.COPY -> "Copy"
            CustomActionType.DELETE -> "Delete"
        }
        val fromPart = source?.let { " from ${it.second}" } ?: ""
        val toPart = destination?.let { " to ${it.second}" } ?: ""
        return "$verb $what$fromPart$toPart"
    }

    /** Strips any stray text around a JSON object the model might add despite instructions. */
    private fun String.extractJsonObject(): String {
        val start = indexOf('{')
        val end = lastIndexOf('}')
        return if (start >= 0 && end > start) substring(start, end + 1) else this
    }

    @Serializable
    private data class AiActionHint(
        val actionType: String,
        val extensions: List<String> = emptyList(),
        val sourceFolderHint: String? = null,
        val sourceVolumeHint: String? = null,
        val destinationFolderHint: String? = null,
        val destinationVolumeHint: String? = null
    )
}
