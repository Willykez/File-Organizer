package com.willykez.files.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.willykez.files.data.model.CommandType
import com.willykez.files.ui.components.FolderPickerDialog
import com.willykez.files.ui.components.GlowButton
import com.willykez.files.ui.screens.ChatScreen
import com.willykez.files.ui.screens.CommandsBottomBar
import com.willykez.files.ui.screens.CommandsScreen
import com.willykez.files.ui.screens.LogScreen
import com.willykez.files.ui.screens.SettingsScreen
import com.willykez.files.ui.theme.Aurora2
import com.willykez.files.ui.theme.BgSpace
import com.willykez.files.ui.theme.BorderGlass
import com.willykez.files.ui.theme.ErrorRed
import com.willykez.files.ui.theme.Glass
import com.willykez.files.ui.theme.Primary
import com.willykez.files.ui.theme.TextDim
import com.willykez.files.ui.theme.TextMain
import com.willykez.files.ui.theme.TextMid
import com.willykez.files.ui.theme.Warn

private val tabTitles = listOf("Commands", "AI Chat", "Log", "Settings")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onRequestStoragePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showConfirmExecute by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgSpace,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("File Organizer", color = TextMain, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 17.sp)
                            Text(state.scanLabel, color = TextMid, fontSize = 11.sp)
                        }
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            state.fileStats?.let {
                                Text(it, color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(end = 10.dp))
                            }
                            GlowButton(
                                label = if (state.scanning) "Scanning…" else "⟳ Scan",
                                color = Color.Black,
                                backgroundColor = Aurora2,
                                enabled = !state.scanning && state.hasStoragePermission,
                                onClick = { viewModel.startScan() }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSpace)
                )
                if (!state.hasStoragePermission) {
                    PermissionBanner(onRequestStoragePermission)
                }
                TabRow(
                    selectedTabIndex = state.activeTab,
                    containerColor = BgSpace,
                    contentColor = Primary,
                    divider = {}
                ) {
                    tabTitles.forEachIndexed { i, title ->
                        Tab(
                            selected = state.activeTab == i,
                            onClick = { viewModel.setActiveTab(i) },
                            text = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (state.activeTab == 0) {
                CommandsBottomBar(
                    state = state,
                    onSelectAll = viewModel::selectAll,
                    onClearSelection = viewModel::clearSelection,
                    onExecute = {
                        if (state.confirmBeforeRun) showConfirmExecute = true else viewModel.executeSelected()
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(BgSpace)) {
            when (state.activeTab) {
                0 -> CommandsScreen(
                    state = state,
                    onSearchChange = viewModel::setSearchQuery,
                    onToggleCategory = viewModel::toggleCategory,
                    onToggleCommand = viewModel::toggleCommand,
                    onSelectAll = viewModel::selectAll,
                    onClearSelection = viewModel::clearSelection,
                    onExecute = {
                        if (state.confirmBeforeRun) showConfirmExecute = true else viewModel.executeSelected()
                    },
                    onScopeChange = viewModel::setStorageScope,
                    onOpenFolderPicker = viewModel::openFolderPicker,
                    onClearFolderScope = viewModel::clearFolderScope,
                    onProtectCurrentFolder = viewModel::protectCurrentFolder,
                    onUnprotectFolder = viewModel::unprotectFolder
                )
                1 -> ChatScreen(
                    state = state,
                    onSend = viewModel::sendChatMessage,
                    onRunDetected = { cmd -> viewModel.executeSingle(cmd) },
                    onConfirmCustomAction = viewModel::confirmCustomAction,
                    onCancelCustomAction = viewModel::cancelCustomAction
                )
                2 -> LogScreen(
                    state = state,
                    onCopyLog = { copyToClipboard(context, viewModel.logAsText()) },
                    onClearLog = viewModel::clearLog,
                    onUndo = viewModel::undoLast,
                    onCancel = viewModel::cancelExecution
                )
                3 -> SettingsScreen(
                    state = state,
                    onSaveApiKey = viewModel::saveApiKey,
                    onClearApiKey = viewModel::clearApiKey,
                    onTestApiKey = viewModel::testApiKey,
                    onToggleAutoOrganize = { enabled ->
                        if (enabled) onRequestNotificationPermission()
                        viewModel.setAutoOrganizeEnabled(enabled)
                    },
                    onToggleNightlyCleanup = { enabled ->
                        if (enabled) onRequestNotificationPermission()
                        viewModel.setNightlyCleanupEnabled(enabled)
                    },
                    onSetSkipHiddenFolders = viewModel::setSkipHiddenFolders,
                    onSetAutoRescanAfterCommands = viewModel::setAutoRescanAfterCommands,
                    onSetConfirmBeforeRun = viewModel::setConfirmBeforeRun,
                    onSetAutoProtectEnabled = viewModel::setAutoProtectEnabled,
                    onSetAutomationNotifications = { enabled ->
                        if (enabled) onRequestNotificationPermission()
                        viewModel.setAutomationNotificationsEnabled(enabled)
                    },
                    onClearScanData = viewModel::clearScanData
                )
            }
        }
    }

    if (showConfirmExecute) {
        val destructive = state.selectedCommands.any {
            it.kind == com.willykez.files.data.model.OperationKind.DELETE
        }
        val protectedCount = state.effectiveProtectedRoots.size
        val scopeLine = state.selectedFolderLabel?.let { "Scope: folder \"$it\" only.\n\n" } ?: ""
        val protectionLine = if (protectedCount > 0) "$protectedCount protected folder(s) will be automatically skipped.\n\n" else ""
        AlertDialog(
            onDismissRequest = { showConfirmExecute = false },
            title = { Text("Run ${state.selectedCommands.size} command(s)?") },
            text = {
                Text(
                    scopeLine + protectionLine + if (destructive)
                        "This includes one or more delete operations. Files removed this way cannot be recovered. Continue?"
                    else
                        "Files will be moved/organized on your device. Move operations can be undone from the Log tab afterward."
                )
            },
            confirmButton = {
                GlowButton(
                    label = "Run",
                    color = Color.Black,
                    backgroundColor = if (destructive) ErrorRed else Primary,
                    onClick = {
                        showConfirmExecute = false
                        viewModel.executeSelected()
                    }
                )
            },
            dismissButton = {
                GlowButton(label = "Cancel", color = TextMid, backgroundColor = Glass, onClick = { showConfirmExecute = false })
            }
        )
    }

    if (state.folderPickerOpen) {
        FolderPickerDialog(
            currentPath = state.folderPickerCurrentPath,
            entries = state.folderPickerEntries,
            loading = state.folderPickerLoading,
            canGoUp = state.folderPickerCurrentPath?.let { current -> state.volumes.none { it.root == current } } ?: false,
            onNavigate = viewModel::navigateFolderPicker,
            onNavigateUp = viewModel::navigateFolderPickerUp,
            onSelect = viewModel::selectFolderScope,
            onDismiss = viewModel::closeFolderPicker
        )
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Warn.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = Warn, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Storage access is required to scan and organize files.",
            color = TextMain, fontSize = 11.sp, modifier = Modifier.weight(1f)
        )
        GlowButton(label = "Grant", color = Color.Black, backgroundColor = Warn, onClick = onRequest)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("File Organizer Log", text))
}
