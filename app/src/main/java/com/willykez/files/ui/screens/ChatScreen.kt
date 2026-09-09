package com.willykez.files.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.files.data.model.CommandType
import com.willykez.files.ui.ChatMessage
import com.willykez.files.ui.CustomActionResolution
import com.willykez.files.ui.PendingCustomAction
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
import com.willykez.files.ui.theme.TextMid

@Composable
fun ChatScreen(
    state: UiState,
    onSend: (String) -> Unit,
    onRunDetected: (CommandType) -> Unit,
    onConfirmCustomAction: (String) -> Unit = {},
    onCancelCustomAction: (String) -> Unit = {}
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.chatMessages, key = { it.id }) { msg ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }
                ) {
                    ChatBubble(msg, onRunDetected, onConfirmCustomAction, onCancelCustomAction)
                }
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
                placeholder = { Text("Ask, or describe a specific move/copy/delete…", color = TextDim) },
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
            SendButton(enabled = input.isNotBlank()) {
                if (input.isNotBlank()) {
                    onSend(input)
                    input = ""
                }
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (enabled) Primary else Glass2),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = if (enabled) Color.Black else TextDim)
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    onRunDetected: (CommandType) -> Unit,
    onConfirmCustomAction: (String) -> Unit,
    onCancelCustomAction: (String) -> Unit
) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 290.dp)) {
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
            msg.pendingCustomAction?.let { pending ->
                Spacer(Modifier.height(6.dp))
                CustomActionCard(
                    messageId = msg.id,
                    pending = pending,
                    resolution = msg.resolution,
                    onConfirm = onConfirmCustomAction,
                    onCancel = onCancelCustomAction
                )
            }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Accent.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text("🤖", fontSize = 12.sp)
    }
}

/** A left accent bar + icon badge, echoing how tool-call/diff blocks read in Claude Code — the
 *  goal is that a detected or custom action reads as a distinct, structured "thing about to
 *  happen" rather than another paragraph of chat text. Implemented inline per-card below (see
 *  [DetectedCommandCard] / [CustomActionCard]) rather than as a shared wrapper, since each needs
 *  slightly different internal layout. */

@Composable
private fun DetectedCommandCard(command: CommandType, onRunDetected: (CommandType) -> Unit) {
    GlassCard(fill = Primary.copy(alpha = 0.08f)) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Primary))
            Row(
                modifier = Modifier.padding(10.dp).weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(command.emoji, fontSize = 14.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill("DETECTED", Primary)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(command.displayName, color = TextMain, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text(command.description, color = TextDim, fontSize = 10.sp, maxLines = 2)
                }
                Spacer(Modifier.width(8.dp))
                GlowButton(label = "Run", color = Color.Black, backgroundColor = Primary, onClick = { onRunDetected(command) })
            }
        }
    }
}

@Composable
private fun CustomActionCard(
    messageId: String,
    pending: PendingCustomAction,
    resolution: CustomActionResolution?,
    onConfirm: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    val accent = when (resolution) {
        CustomActionResolution.CONFIRMED -> Primary
        CustomActionResolution.CANCELLED -> TextDim
        null -> Accent
    }
    GlassCard(fill = accent.copy(alpha = 0.08f)) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🤖", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        when (resolution) {
                            CustomActionResolution.CONFIRMED -> "CONFIRMED"
                            CustomActionResolution.CANCELLED -> "CANCELLED"
                            null -> "AI-BUILT ACTION"
                        },
                        accent
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(pending.action.summary, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                if (pending.matchedFiles.isEmpty()) {
                    Text("No matching files found — nothing would happen.", color = TextDim, fontSize = 11.sp)
                } else {
                    GlassCard(fill = Glass, cornerRadius = 8.dp) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "${pending.matchedFiles.size} FILE(S) AFFECTED",
                                color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            pending.matchedFiles.take(5).forEach { f ->
                                Text(
                                    f.name, color = TextMid, fontSize = 10.5.sp, maxLines = 1,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                            if (pending.matchedFiles.size > 5) {
                                Text("…and ${pending.matchedFiles.size - 5} more", color = TextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (resolution == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlowButton(label = "Cancel", color = TextMid, backgroundColor = Glass2, onClick = { onCancel(messageId) })
                        GlowButton(
                            label = "Confirm & Run",
                            color = Color.Black,
                            backgroundColor = Primary,
                            enabled = pending.matchedFiles.isNotEmpty(),
                            onClick = { onConfirm(messageId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        AssistantAvatar()
        Spacer(Modifier.width(8.dp))
        GlassCard(fill = Glass2) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(14.dp), strokeWidth = 2.dp, color = Primary)
                Spacer(Modifier.width(8.dp))
                Text("Thinking…", color = TextDim, fontSize = 12.sp)
            }
        }
    }
}
