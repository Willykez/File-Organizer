package com.willykez.files.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.willykez.files.ui.theme.BorderGlass
import com.willykez.files.ui.theme.Glass

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    fill: Color = Glass,
    border: Color = BorderGlass,
    cornerRadius: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(fill)
            .border(1.dp, border, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Composable
fun GlowButton(
    label: String,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "glowButtonScale")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.dp, if (enabled) color else color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) color else color.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChipButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Glass)
            .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(text = label, color = color, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
