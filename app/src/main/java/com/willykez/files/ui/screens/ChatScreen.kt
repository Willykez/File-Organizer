package com.willykez.files.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.files.data.model.CommandType
import com.willykez.files.ui.ChatMessage
import com.willykez.files.ui.UiState
import com.willykez.files.ui.components.GlassCard
import com.willykez.files.ui.components.GlowButton
import com.willykez.files.ui.theme.Accent
import com.willykez.files.ui.theme.BorderGlass
import com.willykez.files.ui.theme.Glass
import com.willykez.files.ui.theme.Glass2
import com.willykez.files.ui.theme.Primary
import com.willykez.files.ui.theme.TextDim
import com.willykez.files.ui.theme.TextMain

@Composable
fun ChatScreen(
    state: UiState,
    onSend: (String) -> Unit,
    onRunDetected: (CommandType) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) listState.animateScrollToItem(state.chatMessages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.chatMessages) { msg ->
                ChatBubble(msg, onRunDetected)
            }
            if (state.chatSending) {
                item { TypingBubble() }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask me anything…", color = TextDim) },
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Glass, unfocusedContainerColor = Glass,
                    focusedIndicatorColor = BorderGlass, unfocusedIndicatorColor = BorderGlass,
                    focusedTextColor = TextMain, unfocusedTextColor = TextMain,
                    cursorColor = Primary
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Primary)
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, onRunDetected: (CommandType) -> Unit) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 280.dp)) {
            GlassCard(fill = if (isUser) Accent.copy(alpha = 0.18f) else Glass2) {
                Text(
                    text = msg.text,
                    color = TextMain,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
            msg.detectedCommand?.let { cmd ->
                Spacer(Modifier.height(6.dp))
                DetectedCommandCard(cmd, onRunDetected)
            }
        }
    }
}

@Composable
private fun DetectedCommandCard(command: CommandType, onRunDetected: (CommandType) -> Unit) {
    GlassCard(fill = Primary.copy(alpha = 0.1f)) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(command.emoji, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Detected: ${command.displayName}", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(command.description, color = TextDim, fontSize = 10.sp, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            GlowButton(
                label = "Run",
                color = androidx.compose.ui.graphics.Color.Black,
                backgroundColor = Primary,
                onClick = { onRunDetected(command) }
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        GlassCard(fill = Glass2) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(14.dp), strokeWidth = 2.dp, color = Primary)
                Spacer(Modifier.width(8.dp))
                Text("Thinking…", color = TextDim, fontSize = 12.sp)
            }
        }
    }
}
