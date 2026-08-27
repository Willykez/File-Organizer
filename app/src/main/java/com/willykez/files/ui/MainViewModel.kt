package com.willykez.files.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willykez.files.BuildConfig
import com.willykez.files.data.MetadataManager
import com.willykez.files.data.PreferencesManager
import com.willykez.files.data.model.ActionStatus
import com.willykez.files.data.model.Category
import com.willykez.files.data.model.CommandType
import com.willykez.files.data.model.ExecutionResult
import com.willykez.files.data.model.FileMetadata
import com.willykez.files.domain.CommandExecutor
import com.willykez.files.domain.CommandMatcher
import com.willykez.files.domain.CommandParser
import com.willykez.files.domain.GeminiClient
import com.willykez.files.domain.ScanProgress
import com.willykez.files.domain.StorageScanner
import com.willykez.files.domain.formatSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private suspend fun Flow<Set<String>>.firstOrNullSafe(): Set<String> =
    runCatching { first() }.getOrDefault(emptySet())

enum class LogLevel { SUCCESS, WARN, ERROR, INFO }
data class LogLine(val text: String, val level: LogLevel)
data class ChatMessage(val role: String, val text: String, val detectedCommand: CommandType? = null)

data class UiState(
    val hasStoragePermission: Boolean = false,
    val activeTab: Int = 0,

    val scanning: Boolean = false,
    val scanLabel: String = "Ready to scan",
    val fileStats: String? = null,
    val metadata: List<FileMetadata> = emptyList(),
    val metadataPath: String? = null,
    val metadataExists: Boolean = false,

    val searchQuery: String = "",
    val selectedCommands: Set<CommandType> = emptySet(),
    val expandedCategories: Set<Category> = emptySet(),
    val previewFiles: List<FileMetadata> = emptyList(),

    val chatMessages: List<ChatMessage> = listOf(
        ChatMessage("assistant", "Hello! I'm your AI file organizer. Scan storage first, then ask me anything — I can help organize, clean, find large files, and more.")
    ),
    val chatSending: Boolean = false,

    val logLines: List<LogLine> = listOf(LogLine("Scan storage to begin.", LogLevel.INFO)),
    val statusText: String = "Ready — scan storage to begin.",
    val executing: Boolean = false,

    val lastResult: ExecutionResult? = null,
    val canUndo: Boolean = false,

    val autoOrganizeEnabled: Boolean = false,
    val nightlyCleanupEnabled: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val metadataManager = MetadataManager()
    private val scanner = StorageScanner(application)
    private val executor = CommandExecutor(application)
    private val preferences = PreferencesManager(application)
    private val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY)
    private val chatContextHistory = mutableListOf<String>()

    private var executionJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            val exists = metadataManager.metadataExists()
            if (exists) {
                val meta = metadataManager.loadMetadata()
                _uiState.update {
                    it.copy(
                        metadata = meta,
                        metadataExists = true,
                        metadataPath = metadataManager.metadataPath,
                        fileStats = if (meta.isNotEmpty()) "${meta.size} files  ·  ${formatSize(meta.sumOf(FileMetadata::sizeBytes))}" else null,
                        scanLabel = if (meta.isNotEmpty()) "Scan complete  ·  ${meta.size} files" else it.scanLabel
                    )
                }
            }
        }
        viewModelScope.launch {
            val savedNames = preferences.selectedCommandNames.firstOrNullSafe()
            if (savedNames.isNotEmpty()) {
                val restored = savedNames.mapNotNull { name -> runCatching { CommandType.valueOf(name) }.getOrNull() }.toSet()
                if (restored.isNotEmpty()) {
                    _uiState.update { it.copy(selectedCommands = restored) }
                    refreshPreview()
                }
            }
        }
        viewModelScope.launch {
            preferences.autoOrganizeEnabled.collectLatest { v -> _uiState.update { it.copy(autoOrganizeEnabled = v) } }
        }
        viewModelScope.launch {
            preferences.nightlyCleanupEnabled.collectLatest { v -> _uiState.update { it.copy(nightlyCleanupEnabled = v) } }
        }
    }

    // ---- navigation / permissions --------------------------------------------------------

    fun setActiveTab(tab: Int) = _uiState.update { it.copy(activeTab = tab) }

    fun setStoragePermission(granted: Boolean) = _uiState.update { it.copy(hasStoragePermission = granted) }

    // ---- scanning ---------------------------------------------------------------------

    fun startScan() {
        if (_uiState.value.scanning) return
        _uiState.update {
            it.copy(
                scanning = true,
                scanLabel = "Scanning storage…",
                statusText = "⏳ Indexing files…"
            )
        }
        addLog("⏳ Scan started…", LogLevel.INFO)
        viewModelScope.launch {
            val scanned = scanner.scanAll { _: ScanProgress -> /* could surface live count if desired */ }
            metadataManager.saveMetadata(scanned)
            val totalSize = scanned.sumOf(FileMetadata::sizeBytes)
            _uiState.update {
                it.copy(
                    scanning = false,
                    scanLabel = "Scan complete  ·  ${scanned.size} files",
                    fileStats = "${scanned.size} files  ·  ${formatSize(totalSize)}",
                    metadata = scanned,
                    metadataExists = true,
                    metadataPath = metadataManager.metadataPath,
                    statusText = "✅ ${scanned.size} files indexed."
                )
            }
            addLog("Scan complete: ${scanned.size} files, ${formatSize(totalSize)}", LogLevel.SUCCESS)
            addLog("metadata.json → ${metadataManager.metadataPath}", LogLevel.INFO)
            refreshPreview()
        }
    }

    // ---- command selection / search / preview ------------------------------------------

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun toggleCategory(category: Category) {
        _uiState.update {
            val expanded = it.expandedCategories.toMutableSet()
            if (!expanded.add(category)) expanded.remove(category)
            it.copy(expandedCategories = expanded)
        }
    }

    fun toggleCommand(command: CommandType) {
        _uiState.update {
            val selected = it.selectedCommands.toMutableSet()
            if (!selected.add(command)) selected.remove(command)
            it.copy(selectedCommands = selected)
        }
        refreshPreview()
        viewModelScope.launch { preferences.saveSelectedCommands(_uiState.value.selectedCommands.map { it.name }.toSet()) }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedCommands = CommandType.entries.filter { c -> c != CommandType.UNKNOWN }.toSet()) }
        refreshPreview()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedCommands = emptySet(), previewFiles = emptyList()) }
    }

    private fun refreshPreview() {
        val state = _uiState.value
        if (state.selectedCommands.isEmpty() || state.metadata.isEmpty()) {
            _uiState.update { it.copy(previewFiles = emptyList()) }
            return
        }
        val seen = LinkedHashSet<FileMetadata>()
        outer@ for (cmd in state.selectedCommands) {
            for (meta in state.metadata) {
                if (CommandMatcher.matches(cmd, meta)) {
                    seen += meta
                    if (seen.size >= 200) break@outer
                }
            }
        }
        _uiState.update { it.copy(previewFiles = seen.toList()) }
    }

    // ---- execution --------------------------------------------------------------------

    fun executeSelected() {
        val commands = _uiState.value.selectedCommands.toList()
        if (commands.isEmpty()) return
        runCommands(commands)
    }

    fun executeSingle(command: CommandType) {
        runCommands(listOf(command))
    }

    private fun runCommands(commands: List<CommandType>) {
        val meta = _uiState.value.metadata
        if (meta.isEmpty()) {
            addLog("Scan storage first!", LogLevel.WARN)
            return
        }
        _uiState.update { it.copy(activeTab = 2, executing = true, statusText = "⏳ Running ${commands.size} command(s)…") }
        executionJob = viewModelScope.launch {
            var totalOk = 0
            var totalFail = 0
            var last: ExecutionResult? = null
            for (cmd in commands) {
                addLog("▶ ${cmd.emoji} ${cmd.displayName}", LogLevel.INFO)
                val result = executor.execute(cmd, meta)
                last = result
                for (action in result.actions) {
                    val level = when (action.status) {
                        ActionStatus.SUCCESS -> LogLevel.SUCCESS
                        ActionStatus.SKIPPED -> LogLevel.WARN
                        ActionStatus.FAILED -> LogLevel.ERROR
                    }
                    val prefix = when (action.status) {
                        ActionStatus.SUCCESS -> "✓"
                        ActionStatus.SKIPPED -> "~"
                        ActionStatus.FAILED -> "✗"
                    }
                    addLog("  $prefix ${action.fileName}  [${action.action}]  ${action.detail}", level)
                }
                totalOk += result.succeeded
                totalFail += result.failed
                addLog("  → ${cmd.displayName}: ${result.summary}", LogLevel.INFO)
            }
            val summary = "$totalOk succeeded" + if (totalFail > 0) ", $totalFail failed" else ""
            addLog("═══ Done: $summary ═══", LogLevel.INFO)
            _uiState.update {
                it.copy(
                    executing = false,
                    statusText = (if (totalFail == 0) "✅ " else "⚠️ ") + "Done — $summary",
                    lastResult = last,
                    canUndo = last?.actions?.any { a -> a.undo != null } == true
                )
            }
            // Metadata changed on disk — refresh in-memory copy so the next command's preview is accurate.
            val refreshed = metadataManager.loadMetadata()
            _uiState.update { it.copy(metadata = refreshed) }
            refreshPreview()
        }
    }

    fun cancelExecution() {
        executionJob?.cancel()
        _uiState.update { it.copy(executing = false, statusText = "⏹ Cancelled.") }
        addLog("Execution cancelled by user.", LogLevel.WARN)
    }

    /** New in this rewrite: reverses the moves from the last batch of commands. */
    fun undoLast() {
        val result = _uiState.value.lastResult ?: return
        _uiState.update { it.copy(executing = true, statusText = "⏳ Undoing last operation…") }
        viewModelScope.launch {
            val undoResult = executor.undo(result)
            for (action in undoResult.actions) {
                val level = if (action.status == ActionStatus.SUCCESS) LogLevel.SUCCESS
                else if (action.status == ActionStatus.SKIPPED) LogLevel.WARN else LogLevel.ERROR
                addLog("  ↩ ${action.fileName}  ${action.detail}", level)
            }
            addLog("═══ Undo: ${undoResult.summary} ═══", LogLevel.INFO)
            val refreshed = metadataManager.loadMetadata()
            _uiState.update {
                it.copy(executing = false, statusText = "↩ Undo complete.", metadata = refreshed, canUndo = false, lastResult = null)
            }
        }
    }

    // ---- automation toggles -------------------------------------------------------------

    fun setAutoOrganizeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoOrganizeEnabled(enabled)
            if (enabled) {
                com.willykez.files.automation.AutomationScheduler.schedule(getApplication(), CommandType.AUTO_ORGANIZE_DAILY)
            } else {
                com.willykez.files.automation.AutomationScheduler.cancel(getApplication(), CommandType.AUTO_ORGANIZE_DAILY)
            }
        }
    }

    fun setNightlyCleanupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNightlyCleanupEnabled(enabled)
            if (enabled) {
                com.willykez.files.automation.AutomationScheduler.schedule(getApplication(), CommandType.NIGHTLY_CLEANUP)
            } else {
                com.willykez.files.automation.AutomationScheduler.cancel(getApplication(), CommandType.NIGHTLY_CLEANUP)
            }
        }
    }

    // ---- chat ---------------------------------------------------------------------------

    fun sendChatMessage(text: String) {
        val msg = text.trim()
        if (msg.isEmpty()) return
        _uiState.update { it.copy(chatMessages = it.chatMessages + ChatMessage("user", msg), chatSending = true) }

        viewModelScope.launch {
            val meta = _uiState.value.metadata
            val prompt = buildChatPrompt(meta, msg)
            val reply = gemini.complete(prompt) ?: fallbackChatReply(msg, meta)

            chatContextHistory += "user: $msg"
            chatContextHistory += "assistant: $reply"
            if (chatContextHistory.size > 12) {
                chatContextHistory.subList(0, chatContextHistory.size - 12).clear()
            }

            val detected = if (CommandParser.looksLikeCommand(msg) && meta.isNotEmpty()) {
                CommandParser.matchOffline(msg).takeIf { it != CommandType.UNKNOWN }
            } else null

            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + ChatMessage("assistant", reply, detected),
                    chatSending = false
                )
            }
        }
    }

    private fun buildChatPrompt(meta: List<FileMetadata>, userMessage: String): String = buildString {
        appendLine("You are a smart Android file organizer AI. Be concise (2-3 sentences).")
        if (meta.isNotEmpty()) {
            val totalSize = meta.sumOf(FileMetadata::sizeBytes)
            val byCategory = meta.groupingBy { com.willykez.files.data.FileTypeResolver.resolveCategory(it.extension) }.eachCount()
            appendLine("Storage: ${meta.size} files, ${formatSize(totalSize)}")
            byCategory.forEach { (cat, count) -> appendLine("$cat: $count") }
        } else {
            appendLine("No metadata yet — user needs to scan.")
        }
        chatContextHistory.forEach { appendLine(it) }
        append("User: $userMessage")
    }

    private fun fallbackChatReply(msg: String, meta: List<FileMetadata>): String {
        val l = msg.lowercase()
        return when {
            meta.isEmpty() -> "No metadata yet — tap SCAN first."
            l.contains("how many") || l.contains("count") -> "Your storage has ${meta.size} files total."
            (l.contains("large") || l.contains("big") || l.contains("largest")) ->
                meta.maxByOrNull(FileMetadata::sizeBytes)?.let { "Largest file: \"${it.name}\" at ${formatSize(it.sizeBytes)}." }
                    ?: "No files indexed yet."
            l.contains("duplicate") -> "Use 'Delete Duplicates' in the Cleanup section — SHA-256 verified."
            l.contains("whatsapp") -> "WhatsApp commands are in the Social Media section. Move images, videos, or clean sent junk."
            else -> "I can help organize, clean, and analyze your files. Try asking about duplicates, large files, or file types!"
        }
    }

    // ---- log ------------------------------------------------------------------------

    private fun addLog(text: String, level: LogLevel) {
        _uiState.update { it.copy(logLines = it.logLines + LogLine(text, level)) }
    }

    fun clearLog() {
        _uiState.update { it.copy(logLines = listOf(LogLine("Log cleared.", LogLevel.INFO))) }
    }

    fun logAsText(): String = _uiState.value.logLines.joinToString("\n") { it.text }

    fun metadataStats(): Triple<Int, String, Map<String, Int>> {
        val meta = _uiState.value.metadata
        val totalSize = meta.sumOf(FileMetadata::sizeBytes)
        val byCategory = meta.groupingBy { com.willykez.files.data.FileTypeResolver.resolveCategory(it.extension) }.eachCount()
        return Triple(meta.size, formatSize(totalSize), byCategory)
    }
}
