package com.willykez.files.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.files.data.model.Category
import com.willykez.files.data.model.CommandType
import com.willykez.files.data.model.Cost
import com.willykez.files.domain.formatSize
import com.willykez.files.ui.StorageScope
import com.willykez.files.ui.UiState
import com.willykez.files.ui.VolumeInfo
import com.willykez.files.ui.components.GlassCard
import com.willykez.files.ui.components.GlowButton
import com.willykez.files.ui.theme.Aurora2
import com.willykez.files.ui.theme.BorderGlass
import com.willykez.files.ui.theme.ErrorRed
import com.willykez.files.ui.theme.Glass
import com.willykez.files.ui.theme.Glass2
import com.willykez.files.ui.theme.Gold
import com.willykez.files.ui.theme.Primary
import com.willykez.files.ui.theme.TextDim
import com.willykez.files.ui.theme.TextMain
import com.willykez.files.ui.theme.TextMid
import com.willykez.files.ui.theme.Warn

@Composable
fun CommandsScreen(
    state: UiState,
    onSearchChange: (String) -> Unit,
    onToggleCategory: (Category) -> Unit,
    onToggleCommand: (CommandType) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExecute: () -> Unit,
    onToggleAutoOrganize: (Boolean) -> Unit = {},
    onToggleNightlyCleanup: (Boolean) -> Unit = {},
    onScopeChange: (StorageScope) -> Unit = {},
    onOpenFolderPicker: () -> Unit = {},
    onClearFolderScope: () -> Unit = {},
    onProtectCurrentFolder: () -> Unit = {},
    onUnprotectFolder: (String) -> Unit = {}
) {
    val query = state.searchQuery.lowercase()
    val categories = Category.entries.filter { it != Category.AUTOMATION }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            placeholder = { Text("Search commands…", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMid) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Glass, unfocusedContainerColor = Glass,
                focusedIndicatorColor = BorderGlass, unfocusedIndicatorColor = BorderGlass,
                focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                cursorColor = Primary
            )
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                StorageScopeCard(
                    state = state,
                    onScopeChange = onScopeChange,
                    onOpenFolderPicker = onOpenFolderPicker,
                    onClearFolderScope = onClearFolderScope,
                    onProtectCurrentFolder = onProtectCurrentFolder,
                    onUnprotectFolder = onUnprotectFolder
                )
            }
            item {
                AutomationCard(
                    autoOrganizeEnabled = state.autoOrganizeEnabled,
                    nightlyCleanupEnabled = state.nightlyCleanupEnabled,
                    onToggleAutoOrganize = onToggleAutoOrganize,
                    onToggleNightlyCleanup = onToggleNightlyCleanup
                )
            }
            items(categories) { category ->
                val commands = CommandType.byCategory(category).filter {
                    query.isBlank() || it.displayName.lowercase().contains(query) || it.description.lowercase().contains(query)
                }
                if (commands.isNotEmpty()) {
                    CategorySection(
                        category = category,
                        commands = commands,
                        expanded = category in state.expandedCategories || query.isNotBlank(),
                        selected = state.selectedCommands,
                        onToggleCategory = { onToggleCategory(category) },
                        onToggleCommand = onToggleCommand
                    )
                }
            }

            if (state.previewFiles.isNotEmpty()) {
                item {
                    PreviewPanel(state)
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun StorageScopeCard(
    state: UiState,
    onScopeChange: (StorageScope) -> Unit,
    onOpenFolderPicker: () -> Unit,
    onClearFolderScope: () -> Unit,
    onProtectCurrentFolder: () -> Unit,
    onUnprotectFolder: (String) -> Unit
) {
    val sdCards = state.volumes.filter { it.isRemovable }
    val selectedFolder = state.selectedFolder
    val folderIsProtected = selectedFolder != null && state.effectiveProtectedRoots.any {
        selectedFolder == it || selectedFolder.startsWith("$it/") || it.startsWith("$selectedFolder/")
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("💾 Storage", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            state.volumes.forEach { volume ->
                VolumeRow(volume)
                Spacer(Modifier.height(4.dp))
            }

            if (sdCards.isEmpty()) {
                Text("No SD card detected.", color = TextDim, fontSize = 11.sp)
            } else {
                Spacer(Modifier.height(6.dp))
                Text("Run commands on:", color = TextMid, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScopeChip("All Storage", state.storageScope == StorageScope.ALL && state.selectedFolder == null) {
                        onScopeChange(StorageScope.ALL)
                    }
                    ScopeChip("Internal Only", state.storageScope == StorageScope.INTERNAL && state.selectedFolder == null) {
                        onScopeChange(StorageScope.INTERNAL)
                    }
                    ScopeChip("SD Card Only", state.storageScope == StorageScope.SD_CARD && state.selectedFolder == null) {
                        onScopeChange(StorageScope.SD_CARD)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Folder scope:", color = TextMid, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            if (selectedFolder == null) {
                Text(
                    "Off — commands run against the scope above. Pick a folder to run inside it only.",
                    color = TextDim, fontSize = 10.5.sp
                )
                Spacer(Modifier.height(6.dp))
                ScopeChip("📁 Choose folder…", selected = false, onClick = onOpenFolderPicker)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.selectedFolderLabel ?: "", color = Primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text(selectedFolder, color = TextDim, fontSize = 9.5.sp, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScopeChip("Change", selected = false, onClick = onOpenFolderPicker)
                    ScopeChip("Clear", selected = false, onClick = onClearFolderScope)
                    if (!folderIsProtected) {
                        ScopeChip("🛡 Protect this folder", selected = false, onClick = onProtectCurrentFolder)
                    } else {
                        Text("🛡 Already protected", color = Warn, fontSize = 10.5.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            if (state.effectiveProtectedRoots.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Protected folders (skipped by bulk commands):", color = TextMid, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                state.effectiveProtectedRoots.sorted().forEach { path ->
                    val isUserMarked = path in state.userProtectedFolders
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("🛡", fontSize = 11.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(path.substringAfterLast('/'), color = TextMid, fontSize = 10.5.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        if (isUserMarked) {
                            Text(
                                "remove",
                                color = ErrorRed, fontSize = 10.sp,
                                modifier = Modifier.clickable { onUnprotectFolder(path) }
                            )
                        } else {
                            Text("auto", color = TextDim, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(volume: VolumeInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (volume.isRemovable) "💳" else "📱", fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(volume.label, color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (volume.totalBytes > 0) {
                Text(
                    "${formatSize(volume.freeBytes)} free of ${formatSize(volume.totalBytes)}",
                    color = TextDim, fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun AutomationCard(
    autoOrganizeEnabled: Boolean,
    nightlyCleanupEnabled: Boolean,
    onToggleAutoOrganize: (Boolean) -> Unit,
    onToggleNightlyCleanup: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("🤖 Automation", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Runs automatically in the background — no need to select these manually.", color = TextMid, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            AutomationRow("Daily Auto-Organize", "Moves Downloads & Screenshots every 24h", autoOrganizeEnabled, onToggleAutoOrganize)
            Spacer(Modifier.height(8.dp))
            AutomationRow("Nightly Cleanup", "Deletes temp files & empty folders every 24h", nightlyCleanupEnabled, onToggleNightlyCleanup)
        }
    }
}

@Composable
private fun AutomationRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextDim, fontSize = 10.sp)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun CategorySection(
    category: Category,
    commands: List<CommandType>,
    expanded: Boolean,
    selected: Set<CommandType>,
    onToggleCategory: () -> Unit,
    onToggleCommand: (CommandType) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCategory)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(category.label, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                val selectedCount = commands.count { it in selected }
                if (selectedCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("$selectedCount", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null, tint = TextMid
                )
            }
            if (expanded) {
                commands.forEach { command ->
                    CommandRow(command = command, isSelected = command in selected, onClick = { onToggleCommand(command) })
                }
            }
        }
    }
}

@Composable
private fun CommandRow(command: CommandType, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) Primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (isSelected) Primary else Glass2)
                .border(1.dp, if (isSelected) Primary else BorderGlass, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(command.emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(command.displayName, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(command.description, color = TextMid, fontSize = 11.sp, maxLines = 2)
        }
        CostBadge(command.estimatedCost)
    }
}

@Composable
private fun CostBadge(cost: Cost) {
    val (label, color) = when (cost) {
        Cost.LOW -> "LOW" to Primary
        Cost.MEDIUM -> "MED" to Gold
        Cost.HIGH -> "HIGH" to ErrorRed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PreviewPanel(state: UiState) {
    GlassCard(modifier = Modifier.fillMaxWidth(), fill = Glass2) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "PREVIEW  ·  ${state.previewFiles.size}${if (state.previewFiles.size >= 200) "+" else ""} matching files",
                color = Aurora2, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            state.previewFiles.take(6).forEach { f ->
                Text(
                    "• ${f.name}  (${formatSize(f.sizeBytes)})",
                    color = TextMid, fontSize = 11.sp, maxLines = 1
                )
            }
            if (state.previewFiles.size > 6) {
                Text("…and ${state.previewFiles.size - 6} more", color = TextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun CommandsBottomBar(
    state: UiState,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExecute: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), fill = Glass) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.selectedCommands.isEmpty()) "No commands selected" else "${state.selectedCommands.size} selected",
                color = TextMid, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSelectAll) { Text("All", color = Aurora2, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            IconButton(onClick = onClearSelection) { Text("None", color = TextMid, fontSize = 12.sp) }
            Spacer(Modifier.width(4.dp))
            GlowButton(
                label = if (state.executing) "Running…" else "▶ Execute",
                color = androidx.compose.ui.graphics.Color.Black,
                backgroundColor = Primary,
                enabled = state.selectedCommands.isNotEmpty() && !state.executing && state.metadata.isNotEmpty(),
                onClick = onExecute
            )
        }
    }
}
