package com.willykez.files.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.willykez.files.ui.theme.Aurora1
import com.willykez.files.ui.theme.Aurora2
import com.willykez.files.ui.theme.BgSpace
import com.willykez.files.ui.theme.BgSpace2
import com.willykez.files.ui.theme.Pink

/**
 * A softly drifting three-color glow over a near-black base — replaces the previous flat
 * [BgSpace] fill so every screen sits on the same ambient backdrop instead of a solid color.
 * The motion is slow and subtle by design (18s cycle, low alpha) so it never competes with
 * foreground content or drains battery meaningfully.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraDrift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgSpace)
            .drawBehind {
                val w = size.width
                val h = size.height
                val maxRadius = size.maxDimension

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Aurora1.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(w * (0.15f + drift * 0.1f), h * 0.05f),
                        radius = maxRadius * 0.7f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Aurora2.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(w * (0.9f - drift * 0.1f), h * 0.25f),
                        radius = maxRadius * 0.65f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Pink.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(w * 0.3f, h * (0.95f - drift * 0.05f)),
                        radius = maxRadius * 0.6f
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(colors = listOf(Color.Transparent, BgSpace2.copy(alpha = 0.35f)))
                )
            }
    ) {
        content()
    }
}
