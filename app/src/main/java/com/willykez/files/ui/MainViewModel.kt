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
import com.willykez.files.data.model.CustomAction
import com.willykez.files.data.model.ExecutionResult
import com.willykez.files.data.model.FileMetadata
import com.willykez.files.domain.CommandExecutor
import com.willykez.files.domain.CommandMatcher
import com.willykez.files.domain.CommandParser
import com.willykez.files.domain.CustomCommandParser
import com.willykez.files.domain.GeminiClient
import com.willykez.files.domain.ProtectionRules
import com.willykez.files.domain.ScanProgress
import com.willykez.files.domain.StorageScanner
import com.willykez.files.domain.StorageVolume
import com.willykez.files.domain.StorageVolumeManager
import com.willykez.files.domain.formatSize
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private suspend fun Flow<Set<String>>.firstOrNullSafe(): Set<String> =
    runCatching { first() }.getOrDefault(emptySet())

private suspend fun <T> withContextIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }

enum class LogLevel { SUCCESS, WARN, ERROR, INFO }
data class LogLine(val text: String, val level: LogLevel)

enum class CustomActionResolution { CONFIRMED, CANCELLED }
data class PendingCustomAction(val action: CustomAction, val matchedFiles: List<FileMetadata>)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val detectedCommand: CommandType? = null,
    val pendingCustomAction: PendingCustomAction? = null,
    val resolution: CustomActionResolution? = null
)

/** Which storage volume(s) the Commands tab and executor should act on. Overridden per-run
 *  whenever a specific folder is picked via [UiState.selectedFolder]. */
enum class StorageScope { ALL, INTERNAL, SD_CARD }

data class VolumeInfo(
    val root: String,
    val label: String,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
    val freeBytes: Long,
    val totalBytes: Long
)

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
        ChatMessage(
            role = "assistant",
            text = "Hello! I'm your AI file organizer. Scan storage first, then ask me anything — " +
                "pick a built-in command, or describe something specific like \"move all .mkv files " +
                "from Downloads to the SD card Movies folder\" and I'll build and preview it for you."
        )
    ),
    val chatSending: Boolean = false,

    val logLines: List<LogLine> = listOf(LogLine("Scan storage to begin.", LogLevel.INFO)),
    val statusText: String = "Ready — scan storage to begin.",
    val executing: Boolean = false,

    val lastResult: ExecutionResult? = null,
    val canUndo: Boolean = false,

    val autoOrganizeEnabled: Boolean = false,
    val nightlyCleanupEnabled: Boolean = false,

    val storageScope: StorageScope = StorageScope.ALL,
    val volumes: List<VolumeInfo> = emptyList(),

    // Folder-scoped actions: when set, this overrides storageScope for both preview and execution.
    val selectedFolder: String? = null,
    val selectedFolderLabel: String? = null,
    val folderPickerOpen: Boolean = false,
    val folderPickerCurrentPath: String? = null,
    val folderPickerEntries: List<String> = emptyList(),
    val folderPickerLoading: Boolean = false,

    // Protected folders: auto-detected (source/firmware roots) + user-marked. Bulk organize/move/
    // delete commands skip these unless the user explicitly scoped a command into one via the
    // folder picker.
    val autoProtectedRoots: Set<String> = emptySet(),
    val userProtectedFolders: Set<String> = emptySet()
) {
    val effectiveProtectedRoots: Set<String> get() = autoProtectedRoots + userProtectedFolders
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val metadataManager = MetadataManager()
    private val scanner = StorageScanner(application)
    private val executor = CommandExecutor(application)
    private val preferences = PreferencesManager(application)
    private val volumeManager = StorageVolumeManager(application)
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
                        scanLabel = if (meta.isNotEmpty()) "Scan complete  ·  ${meta.size} files" else it.scanLabel,
                        autoProtectedRoots = if (meta.isNotEmpty()) ProtectionRules.detectProtectedRoots(meta) else emptySet()
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
        viewModelScope.launch {
            val savedScope = runCatching { preferences.storageScope.first() }.getOrDefault("ALL")
            val scope = runCatching { StorageScope.valueOf(savedScope) }.getOrDefault(StorageScope.ALL)
            _uiState.update { it.copy(storageScope = scope) }
            refreshPreview()
        }
        viewModelScope.launch {
            preferences.protectedFolders.collectLatest { folders -> _uiState.update { it.copy(userProtectedFolders = folders) } }
        }
        refreshVolumes()
    }

    // ---- storage volumes (internal + SD card) --------------------------------------------

    /** Re-detects mounted volumes — call after a scan and on launch, since an SD card can be
     *  inserted or removed between app sessions. */
    private fun refreshVolumes() {
        viewModelScope.launch {
            val infos = withContextIo {
                volumeManager.listVolumes().map {
                    VolumeInfo(
                        root = it.root.absolutePath, label = it.label, isRemovable = it.isRemovable,
                        isPrimary = it.isPrimary, freeBytes = it.freeBytes, totalBytes = it.totalBytes
                    )
                }
            }
            _uiState.update { it.copy(volumes = infos) }
        }
    }

    fun setStorageScope(scope: StorageScope) {
        _uiState.update { it.copy(storageScope = scope) }
        refreshPreview()
        viewModelScope.launch { preferences.setStorageScope(scope.name) }
    }

    /** Filters scanned metadata to the current scope — an explicitly picked folder always wins
     *  over the coarser internal/SD-card toggle. */
    private fun scopedMetadata(state: UiState): List<FileMetadata> {
        val folder = state.selectedFolder
        if (folder != null) {
            return state.metadata.filter { it.absolutePath == folder || it.absolutePath.startsWith("$folder/") }
        }
        return when (state.storageScope) {
            StorageScope.ALL -> state.metadata
            StorageScope.INTERNAL -> state.metadata.filter { !it.isRemovable }
            StorageScope.SD_CARD -> state.metadata.filter { it.isRemovable }
        }
    }

    /** Same precedence as [scopedMetadata], but for the physical volume list the executor uses
     *  for volume-wide commands (delete empty folders, clean Gradle cache, etc). A folder scope
     *  becomes a single pseudo-volume rooted at that folder. */
    private fun scopedVolumes(state: UiState): List<StorageVolume> {
        val folder = state.selectedFolder
        if (folder != null) return listOf(volumeManager.asVolume(File(folder)))
        val all = volumeManager.listVolumes()
        return when (state.storageScope) {
            StorageScope.ALL -> all
            StorageScope.INTERNAL -> all.filter { !it.isRemovable }
            StorageScope.SD_CARD -> all.filter { it.isRemovable }
        }
    }

    /** The protected-root set to actually enforce for a run: empty if the user explicitly folder-
     *  scoped into (or onto) a protected root themselves — that's informed consent overriding the
     *  automatic safety net for that one run only. */
    private fun protectionRootsForRun(state: UiState): Set<String> {
        val all = state.effectiveProtectedRoots
        val folder = state.selectedFolder
        return if (folder != null && ProtectionRules.isExplicitlyScopedInto(folder, all)) emptySet() else all
    }

    // ---- folder scope / picker ----------------------------------------------------------

    fun openFolderPicker() {
        val start = _uiState.value.volumes.firstOrNull { it.isPrimary }?.root ?: _uiState.value.volumes.firstOrNull()?.root
        _uiState.update { it.copy(folderPickerOpen = true, folderPickerCurrentPath = start) }
        if (start != null) loadFolderPickerEntries(start)
    }

    fun closeFolderPicker() {
        _uiState.update { it.copy(folderPickerOpen = false) }
    }

    fun navigateFolderPicker(path: String) {
        _uiState.update { it.copy(folderPickerCurrentPath = path) }
        loadFolderPickerEntries(path)
    }

    fun navigateFolderPickerUp() {
        val state = _uiState.value
        val current = state.folderPickerCurrentPath ?: return
        if (state.volumes.any { it.root == current }) return // already at a volume root
        val parent = File(current).parent ?: return
        navigateFolderPicker(parent)
    }

    private fun loadFolderPickerEntries(path: String) {
        _uiState.update { it.copy(folderPickerLoading = true) }
        viewModelScope.launch {
            val entries = withContextIo {
                File(path).listFiles()
                    ?.filter { it.isDirectory && !it.name.startsWith(".") }
                    ?.map { it.absolutePath }
                    ?.sorted()
                    ?: emptyList()
            }
            _uiState.update { it.copy(folderPickerEntries = entries, folderPickerLoading = false) }
        }
    }

    fun selectFolderScope(path: String) {
        val label = File(path).name.ifBlank { path }
        _uiState.update { it.copy(selectedFolder = path, selectedFolderLabel = label, folderPickerOpen = false) }
        refreshPreview()
    }

    fun clearFolderScope() {
        _uiState.update { it.copy(selectedFolder = null, selectedFolderLabel = null) }
        refreshPreview()
    }

    // ---- protected folders ---------------------------------------------------------------

    fun protectCurrentFolder() {
        val folder = _uiState.value.selectedFolder ?: return
        viewModelScope.launch { preferences.addProtectedFolder(folder) }
    }

    fun unprotectFolder(path: String) {
        viewModelScope.launch { preferences.removeProtectedFolder(path) }
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
            val autoRoots = ProtectionRules.detectProtectedRoots(scanned)
            _uiState.update {
                it.copy(
                    scanning = false,
                    scanLabel = "Scan complete  ·  ${scanned.size} files",
                    fileStats = "${scanned.size} files  ·  ${formatSize(totalSize)}",
                    metadata = scanned,
                    metadataExists = true,
                    metadataPath = metadataManager.metadataPath,
                    statusText = "✅ ${scanned.size} files indexed.",
                    autoProtectedRoots = autoRoots
                )
            }
            addLog("Scan complete: ${scanned.size} files, ${formatSize(totalSize)}", LogLevel.SUCCESS)
            addLog("metadata.json → ${metadataManager.metadataPath}", LogLevel.INFO)
            if (autoRoots.isNotEmpty()) {
                addLog(
                    "🛡 Detected ${autoRoots.size} project/firmware folder(s) — excluded from bulk organize/delete by default",
                    LogLevel.INFO
                )
            }
            refreshVolumes()
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
        val scoped = scopedMetadata(state)
        val protectedRoots = protectionRootsForRun(state)
        if (state.selectedCommands.isEmpty() || scoped.isEmpty()) {
            _uiState.update { it.copy(previewFiles = emptyList()) }
            return
        }
        val seen = LinkedHashSet<FileMetadata>()
        outer@ for (cmd in state.selectedCommands) {
            val exempt = cmd in CommandType.protectionExempt
            for (meta in scoped) {
                if (!exempt && protectedRoots.isNotEmpty() && ProtectionRules.isProtected(meta.absolutePath, protectedRoots)) continue
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
        val state = _uiState.value
        val baseMeta = scopedMetadata(state)
        if (baseMeta.isEmpty()) {
            addLog(
                if (state.metadata.isEmpty()) "Scan storage first!"
                else "No files match the current scope.",
                LogLevel.WARN
            )
            return
        }
        val volumesForScope = scopedVolumes(state)
        val protectedRoots = protectionRootsForRun(state)
        _uiState.update { it.copy(activeTab = 2, executing = true, statusText = "⏳ Running ${commands.size} command(s)…") }
        executionJob = viewModelScope.launch {
            var totalOk = 0
            var totalFail = 0
            var last: ExecutionResult? = null
            for (cmd in commands) {
                addLog("▶ ${cmd.emoji} ${cmd.displayName}", LogLevel.INFO)
                val exempt = cmd in CommandType.protectionExempt
                val metaForCmd = if (exempt || protectedRoots.isEmpty()) baseMeta
                else baseMeta.filterNot { ProtectionRules.isProtected(it.absolutePath, protectedRoots) }
                val skipped = baseMeta.size - metaForCmd.size
                if (skipped > 0) addLog("  🛡 Skipping $skipped file(s) inside protected folders", LogLevel.WARN)

                val result = executor.execute(cmd, metaForCmd, volumesForScope, protectedRoots)
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
            refreshVolumes()
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

    // ---- chat / AI custom commands -------------------------------------------------------

    fun sendChatMessage(text: String) {
        val msg = text.trim()
        if (msg.isEmpty()) return
        _uiState.update { it.copy(chatMessages = it.chatMessages + ChatMessage(role = "user", text = msg), chatSending = true) }

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

            // Only attempt to build a custom (non-catalog) action when nothing in the built-in
            // catalog matched and the message actually looks like a file-operation instruction —
            // avoids firing this on ordinary conversation.
            var pending: PendingCustomAction? = null
            if (detected == null && meta.isNotEmpty() && looksLikeCustomInstruction(msg)) {
                val volumes = volumeManager.listVolumes()
                val availableFolders = meta.map { it.parentPath }.toHashSet()
                val customAction = CustomCommandParser.parseWithAi(msg, gemini, volumes, availableFolders)
                if (customAction != null) {
                    val matched = CustomCommandParser.matchFiles(customAction, meta)
                    pending = PendingCustomAction(customAction, matched)
                }
            }

            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + ChatMessage(
                        role = "assistant", text = reply, detectedCommand = detected, pendingCustomAction = pending
                    ),
                    chatSending = false
                )
            }
        }
    }

    private fun looksLikeCustomInstruction(msg: String): Boolean {
        val l = msg.lowercase()
        if (!CommandParser.looksLikeCommand(msg)) return false
        return l.contains("folder") || l.contains(" files") || Regex("""\.[a-z0-9]{2,4}\b""").containsMatchIn(l)
    }

    /** Confirms and runs a pending custom action attached to chat message [messageId] — exactly
     *  the file list and destination the user already reviewed in the confirmation card, nothing
     *  is re-interpreted at this point. */
    fun confirmCustomAction(messageId: String) {
        val msg = _uiState.value.chatMessages.firstOrNull { it.id == messageId } ?: return
        val pending = msg.pendingCustomAction ?: return
        if (msg.resolution != null || pending.matchedFiles.isEmpty()) return

        markResolution(messageId, CustomActionResolution.CONFIRMED)
        _uiState.update { it.copy(activeTab = 2, executing = true, statusText = "⏳ Running custom action…") }
        executionJob = viewModelScope.launch {
            addLog("▶ 🤖 ${pending.action.summary}", LogLevel.INFO)
            val result = executor.executeCustom(pending.action, pending.matchedFiles)
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
            addLog("═══ Done: ${result.summary} ═══", LogLevel.INFO)
            _uiState.update {
                it.copy(
                    executing = false,
                    statusText = "✅ Done — ${result.summary}",
                    lastResult = result,
                    canUndo = result.actions.any { a -> a.undo != null }
                )
            }
            val refreshed = metadataManager.loadMetadata()
            _uiState.update { it.copy(metadata = refreshed) }
            refreshPreview()
        }
    }

    fun cancelCustomAction(messageId: String) {
        markResolution(messageId, CustomActionResolution.CANCELLED)
    }

    private fun markResolution(messageId: String, resolution: CustomActionResolution) {
        _uiState.update { s ->
            s.copy(chatMessages = s.chatMessages.map { if (it.id == messageId) it.copy(resolution = resolution) else it })
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
            l.contains("protect") -> "Pick a folder in the Storage card and tap \"Protect this folder\" to keep bulk commands out of it."
            else -> "I can help organize, clean, and analyze your files — or describe a specific move/copy/delete " +
                "and I'll build it for you to review before anything changes."
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
