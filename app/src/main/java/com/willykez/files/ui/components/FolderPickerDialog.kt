package com.willykez.files.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.files.ui.theme.Aurora2
import com.willykez.files.ui.theme.Glass2
import com.willykez.files.ui.theme.Primary
import com.willykez.files.ui.theme.TextDim
import com.willykez.files.ui.theme.TextMain
import com.willykez.files.ui.theme.TextMid

@Composable
fun FolderPickerDialog(
    currentPath: String?,
    entries: List<String>,
    loading: Boolean,
    canGoUp: Boolean,
    onNavigate: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a folder", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    currentPath ?: "…",
                    color = TextMid, fontSize = 11.sp, maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                if (canGoUp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateUp)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = TextDim, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(".. (up)", color = TextMid, fontSize = 13.sp)
                    }
                }
                if (loading) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.height(24.dp))
                    }
                } else if (entries.isEmpty()) {
                    Text("No subfolders here.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(entries) { path ->
                            val name = path.substringAfterLast('/')
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(path) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = Aurora2, modifier = Modifier.height(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(name, color = TextMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextDim, modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GlowButton(
                label = "Use this folder",
                color = Color.Black,
                backgroundColor = Primary,
                onClick = { currentPath?.let(onSelect) }
            )
        },
        dismissButton = {
            GlowButton(label = "Cancel", color = TextMid, backgroundColor = Glass2, onClick = onDismiss)
        }
    )
}
