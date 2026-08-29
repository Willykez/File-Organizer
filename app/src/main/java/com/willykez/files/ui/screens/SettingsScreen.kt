package com.willykez.files.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onClearScanData: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("AI Integration") }
        item {
            ApiKeySection(
                state = state,
                onSave = onSaveApiKey,
                onClear = onClearApiKey,
                onTest = onTestApiKey
            )
        }

        item { SectionHeader("Automation") }
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

        item { SectionHeader("Scanning") }
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

        item { SectionHeader("Safety") }
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

        item { SectionHeader("Data") }
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

        item { SectionHeader("About") }
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
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(), color = Aurora2, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
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
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Optional Gemini API key — enables richer AI chat replies and more flexible custom-" +
                    "command parsing. Without one, everything still works using offline parsing.",
                color = TextMid, fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))

            if (state.apiKeyConfigured) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("🔑", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Key configured", color = Primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
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
                    placeholder = { Text("Paste your API key…", color = TextDim) },
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
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Stored encrypted on this device only — never sent anywhere except directly to Google's API.",
                color = TextDim, fontSize = 9.5.sp
            )
        }
    }
}
