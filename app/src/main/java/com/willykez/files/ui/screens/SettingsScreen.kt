package com.willykez.files.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.willykez.files.data.model.AiProvider
import com.willykez.files.data.model.AutomationRule
import com.willykez.files.data.model.Category
import com.willykez.files.data.model.CommandType
import com.willykez.files.ui.AutomationRuleDraft
import com.willykez.files.ui.StorageScope
import com.willykez.files.ui.UiState
import com.willykez.files.ui.components.GlassCard
import com.willykez.files.ui.components.GlowButton
import com.willykez.files.ui.theme.Aurora2
import com.willykez.files.ui.theme.BorderGlass
import com.willykez.files.ui.theme.ErrorRed
import com.willykez.files.ui.theme.Glass
import com.willykez.files.ui.theme.Glass2
import com.willykez.files.ui.theme.Primary
import com.willykez.files.ui.theme.TextDim
import com.willykez.files.ui.theme.TextMain
import com.willykez.files.ui.theme.TextMid
import com.willykez.files.ui.theme.Warn

@Composable
fun SettingsScreen(
    state: UiState,
    onSetAiProvider: (AiProvider) -> Unit = {},
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestApiKey: () -> Unit,
    onToggleAutoOrganize: (Boolean) -> Unit,
    onToggleNightlyCleanup: (Boolean) -> Unit,
    onSetSkipHiddenFolders: (Boolean) -> Unit,
    onSetAutoRescanAfterCommands: (Boolean) -> Unit,
    onSetConfirmBeforeRun: (Boolean) -> Unit,
    onSetAutoProtectEnabled: (Boolean) -> Unit,
    onSetAutomationNotifications: (Boolean) -> Unit,
    onClearScanData: () -> Unit,
    onOpenRuleEditor: (AutomationRule?) -> Unit = {},
    onCloseRuleEditor: () -> Unit = {},
    onUpdateRuleDraftName: (String) -> Unit = {},
    onToggleRuleDraftCommand: (CommandType) -> Unit = {},
    onSetRuleDraftInterval: (Int) -> Unit = {},
    onSetRuleDraftStorageScope: (StorageScope) -> Unit = {},
    onOpenFolderPickerForRuleDraft: () -> Unit = {},
    onClearRuleDraftFolder: () -> Unit = {},
    onSaveRuleDraft: () -> Unit = {},
    onDeleteRule: (String) -> Unit = {},
    onSetRuleEnabled: (String, Boolean) -> Unit = { _, _ -> }
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<AutomationRule?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("AI Integration", "🤖") }
        item {
            ApiKeySection(
                state = state,
                onSetProvider = onSetAiProvider,
                onSave = onSaveApiKey,
                onClear = onClearApiKey,
                onTest = onTestApiKey
            )
        }

        item { SectionHeader("Automation", "⏱️") }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsSwitchRow(
                        "Daily Auto-Organize", "Moves Downloads & Screenshots every 24h",
                        state.autoOrganizeEnabled, onToggleAutoOrganize
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingsSwitchRow(
                        "Nightly Cleanup", "Deletes temp files & empty folders every 24h",
                        state.nightlyCleanupEnabled, onToggleNightlyCleanup
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingsSwitchRow(
                        "Notify when automation runs", "Shows a summary notification after each scheduled run",
                        state.automationNotificationsEnabled, onSetAutomationNotifications
                    )
                }
            }
        }

        item { SectionHeader("Custom Automation Rules", "🧩") }
        item {
            Text(
                "Run specific commands on a schedule, scoped to a folder you choose — separate from " +
                    "the two presets above.",
                color = TextMid, fontSize = 11.sp
            )
        }
        items(state.automationRules) { rule ->
            AutomationRuleRow(
                rule = rule,
                onEdit = { onOpenRuleEditor(rule) },
                onDelete = { ruleToDelete = rule },
                onToggleEnabled = { enabled -> onSetRuleEnabled(rule.id, enabled) }
            )
        }
        item {
            GlowButton(
                label = "+ New Automation Rule",
                color = Color.Black, backgroundColor = Primary,
                onClick = { onOpenRuleEditor(null) }
            )
        }

        item { SectionHeader("Scanning", "🔍") }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsSwitchRow(
                        "Skip hidden folders", "Recommended — skips . folders like .git and .gradle during scan",
                        state.skipHiddenFolders, onSetSkipHiddenFolders
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingsSwitchRow(
                        "Auto re-scan after commands", "Full re-index after running commands, instead of reusing the last scan (slower, always accurate)",
                        state.autoRescanAfterCommands, onSetAutoRescanAfterCommands
                    )
                }
            }
        }

        item { SectionHeader("Safety", "🛡️") }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsSwitchRow(
                        "Confirm before running", "Show a review dialog before executing commands",
                        state.confirmBeforeRun, onSetConfirmBeforeRun
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingsSwitchRow(
                        "Auto-protect detected folders", "Skip auto-detected source-code/firmware folders in bulk commands",
                        state.autoProtectEnabled, onSetAutoProtectEnabled
                    )
                    if (!state.autoProtectEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "⚠️ Off — only folders you've manually protected will be skipped.",
                            color = Warn, fontSize = 10.5.sp
                        )
                    }
                }
            }
        }

        item { SectionHeader("Data", "💾") }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        state.metadataPath ?: "No scan data yet.",
                        color = TextDim, fontSize = 10.5.sp, maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(state.fileStats ?: "—", color = TextMid, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    GlowButton(
                        label = "Clear scan data",
                        color = ErrorRed,
                        backgroundColor = ErrorRed.copy(alpha = 0.12f),
                        enabled = state.metadataExists,
                        onClick = { showClearConfirm = true }
                    )
                    Text(
                        "Only clears the on-device index — never touches your actual files.",
                        color = TextDim, fontSize = 9.5.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item { SectionHeader("About", "ℹ️") }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("File Organizer", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Kotlin + Jetpack Compose. Organizes, cleans, and analyzes files across " +
                            "internal storage and SD cards, with protected folders and AI-assisted custom commands.",
                        color = TextMid, fontSize = 11.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear scan data?") },
            text = { Text("This removes the on-device file index (metadata.json). Your actual files are never touched — you'll just need to scan again before running commands.") },
            confirmButton = {
                GlowButton(
                    label = "Clear",
                    color = Color.Black,
                    backgroundColor = ErrorRed,
                    onClick = { showClearConfirm = false; onClearScanData() }
                )
            },
            dismissButton = {
                GlowButton(label = "Cancel", color = TextMid, backgroundColor = Glass, onClick = { showClearConfirm = false })
            }
        )
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete \"${rule.name}\"?") },
            text = { Text("This stops and removes the scheduled rule. It never touches any files that were already moved or deleted by past runs.") },
            confirmButton = {
                GlowButton(
                    label = "Delete", color = Color.Black, backgroundColor = ErrorRed,
                    onClick = { onDeleteRule(rule.id); ruleToDelete = null }
                )
            },
            dismissButton = {
                GlowButton(label = "Cancel", color = TextMid, backgroundColor = Glass, onClick = { ruleToDelete = null })
            }
        )
    }

    if (state.ruleEditorOpen && state.ruleEditorDraft != null) {
        AutomationRuleEditorDialog(
            draft = state.ruleEditorDraft,
            onNameChange = onUpdateRuleDraftName,
            onToggleCommand = onToggleRuleDraftCommand,
            onIntervalChange = onSetRuleDraftInterval,
            onScopeChange = onSetRuleDraftStorageScope,
            onOpenFolderPicker = onOpenFolderPickerForRuleDraft,
            onClearFolder = onClearRuleDraftFolder,
            onSave = onSaveRuleDraft,
            onCancel = onCloseRuleEditor
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        if (icon.isNotEmpty()) {
            Text(icon, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
        }
        Text(title.uppercase(), color = Aurora2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextDim, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun ApiKeySection(
    state: UiState,
    onSetProvider: (AiProvider) -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit
) {
    val context = LocalContext.current
    var input by remember(state.aiProvider) { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Optional — pick a provider and add your own key for richer AI chat replies and more " +
                    "flexible custom-command parsing. Without one, everything still works offline.",
                color = TextMid, fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AiProvider.entries.forEach { provider ->
                    ProviderChip(
                        provider = provider,
                        selected = state.aiProvider == provider,
                        configured = provider in state.apiKeyConfiguredProviders,
                        onClick = { onSetProvider(provider) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                state.aiProvider.description, color = TextDim, fontSize = 9.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            if (state.apiKeyConfigured) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("🔑", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${state.aiProvider.displayName} key configured", color = Primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text(state.apiKeyMaskedPreview ?: "", color = TextDim, fontSize = 10.5.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowButton(
                        label = if (state.apiKeyTesting) "Testing…" else "Test Connection",
                        color = Color.Black, backgroundColor = Aurora2,
                        enabled = !state.apiKeyTesting,
                        onClick = onTest
                    )
                    GlowButton(label = "Remove Key", color = ErrorRed, backgroundColor = ErrorRed.copy(alpha = 0.12f), onClick = onClear)
                }
                state.apiKeyTestMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = if (it.startsWith("✓")) Primary else ErrorRed, fontSize = 11.sp)
                }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste your ${state.aiProvider.displayName} API key…", color = TextDim) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Hide" else "Show", tint = TextMid
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Glass, unfocusedContainerColor = Glass,
                        focusedIndicatorColor = BorderGlass, unfocusedIndicatorColor = BorderGlass,
                        focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                        cursorColor = Primary
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlowButton(
                        label = "Save Key",
                        color = Color.Black, backgroundColor = Primary,
                        enabled = input.isNotBlank(),
                        onClick = { onSave(input); input = "" }
                    )
                    GlowButton(
                        label = "Get a free key ↗",
                        color = TextMid, backgroundColor = Glass2,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(state.aiProvider.keySignupUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Stored encrypted on this device only — never sent anywhere except directly to ${state.aiProvider.displayName}'s API.",
                color = TextDim, fontSize = 9.5.sp
            )
        }
    }
}

@Composable
private fun ProviderChip(
    provider: AiProvider,
    selected: Boolean,
    configured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary.copy(alpha = 0.18f) else Glass2)
            .border(1.dp, if (selected) Primary else BorderGlass, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                provider.displayName, color = if (selected) Primary else TextMain,
                fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
            if (configured) {
                Spacer(Modifier.height(2.dp))
                Text("● configured", color = if (selected) Primary else TextDim, fontSize = 8.5.sp)
            }
        }
    }
}

@Composable
private fun AutomationRuleRow(
    rule: AutomationRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        rule.folderLabel?.let { "📁 $it" } ?: "Scope: ${rule.storageScope.lowercase().replace('_', ' ')}",
                        color = TextDim, fontSize = 10.sp
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.4f))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${rule.commandNames.size} command(s)  ·  every ${formatInterval(rule.intervalHours)}",
                color = TextMid, fontSize = 10.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowButton(label = "Edit", color = Aurora2, backgroundColor = Glass2, onClick = onEdit)
                GlowButton(label = "Delete", color = ErrorRed, backgroundColor = ErrorRed.copy(alpha = 0.12f), onClick = onDelete)
            }
        }
    }
}

private fun formatInterval(hours: Int): String = when {
    hours < 24 -> "${hours}h"
    hours == 24 -> "day"
    hours % 24 == 0 -> "${hours / 24}d"
    else -> "${hours}h"
}

private val RULE_INTERVAL_OPTIONS = listOf(1, 6, 12, 24, 48, 168)
private val RULE_INTERVAL_LABELS = listOf("1h", "6h", "12h", "Daily", "2 days", "Weekly")

@Composable
private fun AutomationRuleEditorDialog(
    draft: AutomationRuleDraft,
    onNameChange: (String) -> Unit,
    onToggleCommand: (CommandType) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onScopeChange: (StorageScope) -> Unit,
    onOpenFolderPicker: () -> Unit,
    onClearFolder: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val categories = Category.entries.filter { it != Category.AUTOMATION }
    val canSave = draft.name.isNotBlank() && draft.commands.isNotEmpty()

    Dialog(onDismissRequest = onCancel) {
        GlassCard(modifier = Modifier.fillMaxWidth(), fill = com.willykez.files.ui.theme.BgSpace) {
            Column(modifier = Modifier.padding(16.dp).heightIn(max = 560.dp)) {
                Text(
                    if (draft.isNew) "New Automation Rule" else "Edit Automation Rule",
                    color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Rule name, e.g. \"Clean my Downloads\"", color = TextDim) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Glass, unfocusedContainerColor = Glass,
                        focusedIndicatorColor = BorderGlass, unfocusedIndicatorColor = BorderGlass,
                        focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                        cursorColor = Primary
                    )
                )

                Spacer(Modifier.height(14.dp))
                Text("SCOPE", color = Aurora2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (draft.folderPath != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📁 ${draft.folderLabel}", color = Primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(draft.folderPath, color = TextDim, fontSize = 9.sp, maxLines = 1)
                        }
                        RuleChip("Change", false, onOpenFolderPicker)
                        Spacer(Modifier.width(6.dp))
                        RuleChip("Clear", false, onClearFolder)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuleChip("All Storage", draft.storageScope == StorageScope.ALL) { onScopeChange(StorageScope.ALL) }
                        RuleChip("Internal", draft.storageScope == StorageScope.INTERNAL) { onScopeChange(StorageScope.INTERNAL) }
                        RuleChip("SD Card", draft.storageScope == StorageScope.SD_CARD) { onScopeChange(StorageScope.SD_CARD) }
                    }
                    Spacer(Modifier.height(6.dp))
                    RuleChip("📁 Or pick a specific folder…", false, onOpenFolderPicker)
                }

                Spacer(Modifier.height(14.dp))
                Text("RUNS EVERY", color = Aurora2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RULE_INTERVAL_OPTIONS.forEachIndexed { i, hours ->
                        RuleChip(RULE_INTERVAL_LABELS[i], draft.intervalHours == hours) { onIntervalChange(hours) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("COMMANDS (${draft.commands.size} selected)", color = Aurora2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 220.dp)) {
                    categories.forEach { category ->
                        item {
                            Text(category.label, color = TextMid, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                        }
                        items(CommandType.byCategory(category)) { command ->
                            RuleCommandRow(command, command in draft.commands) { onToggleCommand(command) }
                        }
                    }
                }

                if (draft.commands.any { it.kind == com.willykez.files.data.model.OperationKind.DELETE }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠️ Includes delete command(s) — these will run unattended with no confirmation.",
                        color = Warn, fontSize = 10.5.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowButton(label = "Cancel", color = TextMid, backgroundColor = Glass2, onClick = onCancel)
                    GlowButton(label = "Save Rule", color = Color.Black, backgroundColor = Primary, enabled = canSave, onClick = onSave)
                }
            }
        }
    }
}

@Composable
private fun RuleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Primary.copy(alpha = 0.2f) else Glass2)
            .border(1.dp, if (selected) Primary else BorderGlass, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Primary else TextMid, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RuleCommandRow(command: CommandType, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(18.dp).width(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) Primary else Glass2)
                .border(1.dp, if (checked) Primary else BorderGlass, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.height(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(command.emoji, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(command.displayName, color = TextMain, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
    }
}
