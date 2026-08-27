package com.willykez.files.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.files.ui.LogLevel
import com.willykez.files.ui.LogLine
import com.willykez.files.ui.UiState
import com.willykez.files.ui.components.ChipButton
import com.willykez.files.ui.components.GlassCard
import com.willykez.files.ui.theme.ErrorRed
import com.willykez.files.ui.theme.Glass2
import com.willykez.files.ui.theme.Primary
import com.willykez.files.ui.theme.TextMain
import com.willykez.files.ui.theme.TextMid
import com.willykez.files.ui.theme.Warn

@Composable
fun LogScreen(
    state: UiState,
    onCopyLog: () -> Unit,
    onClearLog: () -> Unit,
    onUndo: () -> Unit,
    onCancel: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.logLines.size) {
        if (state.logLines.isNotEmpty()) listState.animateScrollToItem(state.logLines.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GlassCard(modifier = Modifier.fillMaxWidth().padding(14.dp), fill = Glass2) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(state.statusText, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (state.executing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipButton("Copy", TextMid, onClick = onCopyLog)
                    ChipButton("Clear", TextMid, onClick = onClearLog)
                    if (state.canUndo) ChipButton("↩ Undo", Warn, onClick = onUndo)
                    if (state.executing) ChipButton("Cancel", ErrorRed, onClick = onCancel)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(state.logLines) { line -> LogRow(line) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val color = when (line.level) {
        LogLevel.SUCCESS -> Primary
        LogLevel.WARN -> Warn
        LogLevel.ERROR -> ErrorRed
        LogLevel.INFO -> TextMid
    }
    Text(
        text = line.text,
        color = color,
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    )
}
